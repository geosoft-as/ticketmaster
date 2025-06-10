package no.geosoft.ticketmaster;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.json.JsonWriter;

/**
 * Class representing an Azure instance.
 *
 * @author <a href="mailto:jacob.dreyer@geosoft.no">Jacob Dreyer</a>
 */
public final class Azure
{
  /** The logger instance. */
  private static final Logger logger_ = Logger.getLogger(Azure.class.getName());

  private static final String ORGANIZATION = "<organization>";
  private static final String PROJECT = "<project>";
  private static final String TOKEN = "<PAT>";

  /** Azure base URL. */
  private static final String BASE_URL = "https://dev.azure.com/" + ORGANIZATION + "/" + PROJECT;

  /** Apparently needed on (some of) the URLs */
  private static final String API_VERSION = "7.1-preview.3";

  /** HTTP authorization header. */
  private static final String AUTHORIZATION_HEADER = "Basic " + Base64.getEncoder().encodeToString((":" + TOKEN).getBytes(StandardCharsets.UTF_8));

  /** All work items loaded. */
  private final List<AzureWorkItem> workItems_ = new ArrayList<>();

  /**
   * Create an instance <em>representing</em> the Azure back-end.
   */
  public Azure()
  {
    // Nothing
  }

  /**
   * Process the attachments of the specified workItem, and upload the content from the
   * corresponding YouTrack attachment and set the url accordingly.
   *
   * @param workItem  Work item to process. Non-null.
   */
  public void uploadAttachments(AzureWorkItem workItem)
  {
    assert workItem != null : "workItem cannot be null";

    String baseUrl = BASE_URL + "/_apis/wit/attachments";

    int attachmentNo = 1;
    for (AzureAttachment attachment : workItem.getAttachments()) {

      // Skip if there is no content
      if (attachment.getContent() == null)
        continue;

      String extension = attachment.getExtension();

      String fileName = "attachment-" + workItem.getUniqueId() + "-" + attachmentNo + "." + extension;
      String urlString = baseUrl + "?filename=" + fileName + "&api-version=" + API_VERSION;

      URL url = Util.newUrl(urlString);

      logger_.log(Level.INFO, "Uploading attachment: " + attachment + " to " + fileName);

      HttpURLConnection connection = null;
      OutputStream outputStream = null;
      InputStream inputStream = null;

      try {
        // Make connection
        connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Authorization", AUTHORIZATION_HEADER);
        connection.setRequestProperty("Content-Type", "application/octet-stream");
        connection.setRequestProperty("Accept", "application/json");

        // Upload file data
        outputStream = connection.getOutputStream();
        outputStream.write(attachment.getContent());

        // Capture response with destination URL
        inputStream = connection.getInputStream();
        JsonObject response = Json.createReader(inputStream).readObject();

        String attachmentUrl = response.getString("url");

        // Update attachment accordingly
        attachment.setFileName(fileName);
        attachment.setUrl(attachmentUrl);
        attachmentNo++;
      }
      catch (IOException exception) {
        logger_.log(Level.WARNING, "Attachment upload failed: " + attachment, exception);
      }
      finally {
        Util.close(outputStream);
        Util.close(inputStream);
        Util.close(connection);
      }
    }
  }

  /**
   * Push the attachments of the specified work item.
   * This associates the work item with the given attachment. The attachments themselves
   * must be uploaded separately, see uploadAttachments().
   *
   * @param workItem  Work item of attachments to push. Non-null.
   * @throws  IllegalArgumentException  If workItem is null.
   */
  public void pushAttachments(AzureWorkItem workItem)
  {
    if (workItem == null)
      throw new IllegalArgumentException("workItem cannot be null");

    int id = workItem.getId();

    String urlString = BASE_URL + "/_apis/wit/workitems/" + id + "?api-version=" + API_VERSION;
    URL url = Util.newUrl(urlString);

    int nAttachments = workItem.getAttachments().size();
    int attachmentNo = 1;
    for (AzureAttachment attachment : workItem.getAttachments()) {
      logger_.log(Level.INFO, "Push attachment " + (attachmentNo++) + ":" + nAttachments  + " to " + id);

      if (attachment.getUrl() == null)
        continue;

      JsonArrayBuilder bodyBuilder = Json.createArrayBuilder()
                                     .add(Json.createObjectBuilder()
                                          .add("op", "add")
                                          .add("path", "/relations/-")
                                          .add("value", Json.createObjectBuilder()
                                               .add("rel", "AttachedFile")
                                               .add("url", attachment.getUrl())));

      String bodyJson = bodyBuilder.build().toString();

      HttpURLConnection connection = null;
      OutputStream outputStream = null;
      InputStream inputStream = null;

      try {
        connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("X-HTTP-Method-Override", "PATCH");
        connection.setRequestProperty("Content-Type", "application/json-patch+json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Authorization", AUTHORIZATION_HEADER);
        connection.setDoOutput(true);

        outputStream = connection.getOutputStream();
        outputStream.write(bodyJson.getBytes(StandardCharsets.UTF_8));

        int responseCode = connection.getResponseCode();
        if (responseCode >= 200 && responseCode < 300) {
          logger_.log(Level.INFO, "Attachment pushed successfully");
        }
        else {
          inputStream = connection.getInputStream();
          String error = Util.getError(inputStream);
          logger_.log(Level.INFO, "Attachment push failed: " + responseCode + " " + error);
        }
      }
      catch (IOException exception) {
        logger_.log(Level.WARNING, "Attachment push failed", exception);
      }
      finally {
        Util.close(outputStream);
        Util.close(inputStream);
        Util.close(connection);
      }
    }
  }

  /**
   * Push all comments associated with the specified work item.
   *
   * @param workItem  Work item of comments to push. Non-null.
   * @throws IllegalArgumentException  If workItem is null.
   */
  public void pushComments(AzureWorkItem workItem)
  {
    if (workItem == null)
      throw new IllegalArgumentException("workItem cannot be null");

    int id = workItem.getId();

    String urlString = BASE_URL + "/_apis/wit/workItems/" + id + "/comments?api-version=" + API_VERSION;
    URL url = Util.newUrl(urlString);

    int nComments = workItem.getComments().size();
    int commentNo = 1;
    for (AzureComment comment : workItem.getComments()) {
      logger_.log(Level.INFO, "Push comment " + (commentNo++) + ":" + nComments + " to " + id);

      String text = comment.getExtendedText();

      JsonObject bodyJson = Json.createObjectBuilder()
                            .add("text", text)
                            .build();

      HttpURLConnection connection = null;
      OutputStream outputStream = null;
      InputStream inputStream = null;
      try {
        connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", AUTHORIZATION_HEADER);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        outputStream = connection.getOutputStream();
        outputStream.write(bodyJson.toString().getBytes(StandardCharsets.UTF_8));

        int responseCode = connection.getResponseCode();
        if (responseCode >= 200 && responseCode < 300) {
          logger_.log(Level.INFO, "Comment pushed successfully");
        }
        else {
          inputStream = connection.getInputStream();
          String error = Util.getError(inputStream);
          logger_.log(Level.INFO, "Comment pushed failed: " + responseCode + " " + error);
        }
      }
      catch (IOException exception) {
        logger_.log(Level.WARNING, "Unable to push comment: " + comment, exception);
      }
      finally {
        Util.close(outputStream);
        Util.close(inputStream);
        Util.close(connection);
      }
    }
  }

  /**
   * Work items must be created as "New", so after we have done that we update
   * to the correct state.
   *
   * @param workItem  Work item to update state on. Non-null.
   * @throws IllegalArgumentException  If workItem is null.
   */
  public void pushState(AzureWorkItem workItem)
  {
    String state = workItem.getState();

    logger_.log(Level.INFO, "Setting state of " + workItem.getId() + " to '" + state + "'");

    String urlString = BASE_URL + "/_apis/wit/workitems/" + workItem.getId() + "?api-version=" + API_VERSION;
    URL url = Util.newUrl(urlString);

    HttpURLConnection connection = null;
    OutputStream outputStream = null;
    InputStream inputStream = null;

    try {
      connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("POST");
      connection.setRequestProperty("X-HTTP-Method-Override", "PATCH");
      connection.setRequestProperty("Authorization", AUTHORIZATION_HEADER);
      connection.setRequestProperty("Content-Type", "application/json-patch+json");
      connection.setRequestProperty("Accept", "application/json");
      connection.setDoOutput(true);

      JsonArrayBuilder bodyBuilder = Json.createArrayBuilder()
                                     .add(Json.createObjectBuilder() // State
                                          .add("op", "add")
                                          .add("path", "/fields/System.State")
                                          .add("value", state));
      String bodyJson = bodyBuilder.build().toString();

      outputStream = connection.getOutputStream();
      outputStream.write(bodyJson.getBytes(StandardCharsets.UTF_8));

      int responseCode = connection.getResponseCode();
      if (responseCode >= 200 && responseCode < 300) {
        logger_.log(Level.INFO, "State pushed successfully: " + workItem.getId());
      }
      else {
        inputStream = connection.getInputStream();
        String error = Util.getError(inputStream);
        logger_.log(Level.INFO, "State push failed: " + responseCode + " " + error);
      }
    }
    catch (IOException exception) {
      logger_.log(Level.WARNING, "State push failed", exception);
    }
    finally {
      Util.close(outputStream);
      Util.close(inputStream);
      Util.close(connection);
    }
  }

  /**
   * Check if the specified link already exists in the back-end.
   * <p>
   * NOTE: Doesn't seem to work, i.e. trigger on any existing links, but on the other hand it
   * seems that it does no harm pushing the same link multiple times. And in that case this
   * method isn't needed.
   *
   * @param workItemId    ID or work item origin of the link. Non-null.
   * @param targetUrl     Target link URL. Non-null.
   * @param relationType  Relation type. Non-null.
   * @return  True if the link exists already, false otherwise.
   */
  private boolean linkExists(int workItemId, String targetUrl, String relationType)
  {
    assert targetUrl != null : "targetUrl cannot be null";
    assert relationType != null : "relationType cannot be null";

    String urlString = BASE_URL + "/_apis/wit/workitems/" + workItemId + "?$expand=relations&api-version=" + API_VERSION;
    URL url = Util.newUrl(urlString);

    HttpURLConnection connection = null;
    InputStream inputStream = null;

    String targetUrlNormalized = Util.normalizeUrl(targetUrl);

    try {
      connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("GET");
      connection.setRequestProperty("Authorization", AUTHORIZATION_HEADER);
      connection.setRequestProperty("Accept", "application/json");

      inputStream = connection.getInputStream();
      JsonReader reader = Json.createReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
      JsonObject jsonResponse = reader.readObject();
      JsonArray relations = jsonResponse.getJsonArray("relations");

      if (relations != null) {
        for (JsonValue relationValue : relations) {
          JsonObject relation = relationValue.asJsonObject();
          String rel = relation.getString("rel");
          String urlLink = Util.normalizeUrl(relation.getString("url"));

          // Check if the relation type and URL match
          if (rel.equals(relationType) && urlLink.equals(targetUrlNormalized))
            return true; // Link already exists
        }
      }
    }
    catch (IOException exception) {
      logger_.log(Level.WARNING, "Error while checking for existing link", exception);
    }
    finally {
      Util.close(inputStream);
      Util.close(connection);
    }

    return false; // Link does not exist
  }

  /**
   * Push link information for the specified work item.
   *
   * @param workItem  Work item to have link information pushed. Non-null.
   */
  public void pushLinks(AzureWorkItem workItem)
  {
    if (workItem == null)
      throw new IllegalArgumentException("workItem cannot be null");

    int id = workItem.getId();

    for (AzureLink link : workItem.getLinks()) {
      AzureWorkItem linkedWorkItem = link.getLinkedWorkItem();
      String targetUrl = BASE_URL + "/_apis/wit/workItems/" + linkedWorkItem.getId();

      String relationType = null;
      switch (link.getType()) {
        case DUPLICATE  : relationType = "System.LinkTypes.Duplicate-Reverse"; break;
        case DEPENDS_ON : relationType = "System.LinkTypes.Dependency-Reverse"; break;
        case SUBTASK    : relationType = "System.LinkTypes.Hierarchy-Reverse"; break;
        case RELATED    : relationType = "System.LinkTypes.Related"; break;
        default :
          assert false : "Unexpected link type: " + link.getType();
      }

      if (linkExists(id, targetUrl, relationType)) {
        logger_.log(Level.INFO, "Link already exists: " + id);
        continue;
      }

      JsonArrayBuilder bodyBuilder = Json.createArrayBuilder()
                                     .add(Json.createObjectBuilder()
                                          .add("op", "add")
                                          .add("path", "/relations/-")
                                     .add("value", Json.createObjectBuilder()
                                          .add("rel", relationType)
                                          .add("url", targetUrl)
                                     .build()));
      String bodyJson = bodyBuilder.build().toString();

      String urlString = BASE_URL + "/_apis/wit/workitems/" + id + "?api-version=" + API_VERSION;
      URL url = Util.newUrl(urlString);

      HttpURLConnection connection = null;
      OutputStream outputStream = null;
      InputStream inputStream = null;

      try {
        connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("X-HTTP-Method-Override", "PATCH");
        connection.setRequestProperty("Authorization", AUTHORIZATION_HEADER);
        connection.setRequestProperty("Content-Type", "application/json-patch+json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setDoOutput(true);

        outputStream = connection.getOutputStream();
        outputStream.write(bodyJson.getBytes(StandardCharsets.UTF_8));

        int responseCode = connection.getResponseCode();
        if (responseCode >= 200 && responseCode < 300) {
          logger_.log(Level.INFO, "Link pushed successfully: " + id + " -> " + linkedWorkItem.getId());
        }
        else {
          inputStream = connection.getInputStream();
          String error = Util.getError(inputStream);
          logger_.log(Level.INFO, "Link push failed: " + id + " -> " + linkedWorkItem.getId() + ": " + responseCode + " " + error);
        }
      }
      catch (IOException exception) {
        logger_.log(Level.WARNING, "Link push failed: " + id + " -> " + linkedWorkItem.getId(), exception);
      }
      finally {
        Util.close(inputStream);
        Util.close(outputStream);
        Util.close(connection);
      }
    }
  }

  /**
   * Push the specified Azure work item to the back-end.
   *
   * @param workItem  Work item to push. Non-null.
   * @throws IllegalArgumentEAxception  If workItem is null.
   */
  public void pushWorkItem(AzureWorkItem workItem)
  {
    if (workItem == null)
      throw new IllegalArgumentException("workItem cannot be null");

    String urlString = BASE_URL + "/_apis/wit/workitems/$" + Util.urlEncode(workItem.getWorkItemType()) + "?api-version=" + API_VERSION + "&bypassRules=true";
    URL url = Util.newUrl(urlString);

    String title = workItem.getTitle();
    String description = workItem.getExtendedDescription();
    String assignedToEmail = workItem.getAssignedTo() != null && workItem.getAssignedTo().getEmail() != null ? workItem.getAssignedTo().getEmail() : "";
    String createdByEmail = workItem.getCreatedBy().getEmail();
    String testerEmail = workItem.getTester() != null && workItem.getTester().getEmail() != null ? workItem.getTester().getEmail() : "";
    String tags = workItem.getTagsAsString();
    String project = workItem.getProject();
    String product = workItem.getProduct();
    boolean isBlocked = workItem.isBlocked();
    int priority = workItem.getPriority();
    String activity = workItem.getActivity();

    String systemInfo = workItem.getSystemInfo();
    if (systemInfo == null)
      systemInfo = "";

    String foundIn = workItem.getFoundIn();
    if (foundIn == null)
      foundIn = "";

    String stepsToReproduce = workItem.getStepsToReproduce();

    String startDate = Util.toString(workItem.getStartDate());
    if (startDate == null)
      startDate = "";

    String targetDate = Util.toString(workItem.getTargetDate());
    if (targetDate == null)
      targetDate = "";


    String targetRelease = workItem.getTargetRelease();
    if (targetRelease == null)
      targetRelease = "";

    String plcVersion = workItem.getPlcVersion();
    if (plcVersion == null)
      plcVersion = "";

    String targetDtApp = workItem.getTargetDtApp();
    if (targetDtApp == null)
      targetDtApp = "";

    String areaPath = workItem.getAreaPath();
    if (areaPath == null)
      areaPath = "<default area>";

    // Got a NPE, not sure why
    if (createdByEmail == null)
      createdByEmail = "<default email>";

    /*
    */

    // Build JSON PATCH body
    JsonArrayBuilder bodyBuilder = Json.createArrayBuilder()
                                   .add(Json.createObjectBuilder() // Title
                                        .add("op", "add")
                                        .add("path", "/fields/System.Title")
                                        .add("value", title))
                                   .add(Json.createObjectBuilder() // Area
                                        .add("op", "add")
                                        .add("path", "/fields/System.AreaPath")
                                        .add("value", PROJECT + "\\\\" + areaPath))
                                   .add(Json.createObjectBuilder() // Description
                                        .add("op", "add")
                                        .add("path", "/fields/System.Description")
                                        .add("value", description))
                                   .add(Json.createObjectBuilder() // Tags
                                        .add("op", "add")
                                        .add("path", "/fields/System.Tags")
                                        .add("value", tags))
                                   .add(Json.createObjectBuilder() // Blocked
                                        .add("op", "add")
                                        .add("path", "/fields/Microsoft.VSTS.CMMI.Blocked")
                                        .add("value", isBlocked ? "Yes" : "No"))
                                   .add(Json.createObjectBuilder() // Project
                                        .add("op", "add")
                                        .add("path", "/fields/Custom.Project")
                                        .add("value", project))
                                   .add(Json.createObjectBuilder() // Product
                                        .add("op", "add")
                                        .add("path", "/fields/Custom.Product")
                                        .add("value", product))
                                   .add(Json.createObjectBuilder() // Tester
                                        .add("op", "add")
                                        .add("path", "/fields/Custom.Tester")
                                        .add("value", testerEmail))
                                   .add(Json.createObjectBuilder() // Priority
                                        .add("op", "add")
                                        .add("path", "/fields/Microsoft.VSTS.Common.Priority")
                                        .add("value", priority))
                                   .add(Json.createObjectBuilder() // Activity
                                        .add("op", "add")
                                        .add("path", "/fields/Microsoft.VSTS.Common.Activity")
                                        .add("value", activity))
                                   .add(Json.createObjectBuilder() // Created by
                                        .add("op", "add")
                                        .add("path", "/fields/System.CreatedBy")
                                        .add("value", createdByEmail))
                                   .add(Json.createObjectBuilder() // Assigned to
                                        .add("op", "add")
                                        .add("path", "/fields/System.AssignedTo")
                                        .add("value", assignedToEmail))
                                   .add(Json.createObjectBuilder() // Start Date
                                        .add("op", "add")
                                        .add("path", "/fields/Microsoft.VSTS.Scheduling.StartDate")
                                        .add("value", startDate))
                                   .add(Json.createObjectBuilder() // Target Date
                                        .add("op", "add")
                                        .add("path", "/fields/Microsoft.VSTS.Scheduling.TargetDate")
                                        .add("value", targetDate))
                                   .add(Json.createObjectBuilder() // System Info
                                        .add("op", "add")
                                        .add("path", "/fields/Microsoft.VSTS.TCM.SystemInfo")
                                        .add("value", systemInfo))
                                   .add(Json.createObjectBuilder() // Found in
                                        .add("op", "add")
                                        .add("path", "/fields/Microsoft.VSTS.CMMI.FoundInEnvironment")
                                        .add("value", foundIn))
                                   .add(Json.createObjectBuilder() // Steps to reproduce
                                        .add("op", "add")
                                        .add("path", "/fields/Microsoft.VSTS.TCM.ReproSteps")
                                        .add("value", stepsToReproduce));

    String bodyJson = bodyBuilder.build().toString();

    HttpURLConnection connection = null;
    OutputStream outputStream = null;
    InputStream inputStream = null;

    try {
      connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("POST");
      connection.setRequestProperty("Authorization", AUTHORIZATION_HEADER);
      connection.setRequestProperty("Content-Type", "application/json-patch+json");
      connection.setRequestProperty("Accept", "application/json");
      connection.setDoOutput(true);

      outputStream = connection.getOutputStream();
      outputStream.write(bodyJson.getBytes(StandardCharsets.UTF_8));

      // To get the ID of the created work item
      inputStream = connection.getInputStream();

      int responseCode = connection.getResponseCode();
      if (responseCode >= 200 && responseCode < 300) {
        JsonObject response = Json.createReader(inputStream).readObject();
        int id = response.getInt("id");
        workItem.setId(id);

        logger_.log(Level.INFO, "WorkItem push successfully: " + id);
      }
      else {
        inputStream = connection.getInputStream();
        String error = Util.getError(inputStream);
        logger_.log(Level.INFO, "WorkItem push failed: " + responseCode + " " + error);
      }
    }
    catch (IOException exception) {
      if (connection.getErrorStream() != null)
        System.out.println(Util.getError(connection.getErrorStream()));

      logger_.log(Level.WARNING, "WorkItem push failed", exception);
    }
    finally {
      Util.close(outputStream);
      Util.close(inputStream);
      Util.close(connection);
    }
  }

  /**
   * Return all work item IDs.
   *
   * @return  All work item IDs. Never null.
   */
  private List<Integer> pullAllWorkItemIds()
  {
    int start = 1000;
    int batchSize = 10000;

    String urlString = BASE_URL + "/_apis/wit/wiql?api-version=7.0"; // + API_VERSION;
    URL url = Util.newUrl(urlString);

    List<Integer> workItemIds = new ArrayList<>();

    while (true) {
      String sql = "SELECT " +
                   "  [System.Id] " +
                   "FROM " +
                   "  WorkItems " +
                   "WHERE " +
                   "  [System.TeamProject] = '" + PROJECT + "'" + " AND " +
                   "  [System.IsDeleted] <> true" + " AND " +
                   "  [System.Id] >= " + start + " AND " +
                   "  [System.Id] < " + (start + batchSize);

      JsonObject query = Json.createObjectBuilder().add("query", sql).build();

      HttpURLConnection connection = null;
      InputStream responseStream = null;
      InputStream errorStream = null;

      try {
        connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", AUTHORIZATION_HEADER);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setRequestProperty("Accept", "application/json");
        connection.setDoOutput(true);

        try (OutputStream outputStream = connection.getOutputStream();
             JsonWriter jsonWriter = Json.createWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
          jsonWriter.write(query);
        }

        // Read response
        responseStream = connection.getInputStream();
        InputStreamReader utf8Reader = new InputStreamReader(responseStream, StandardCharsets.UTF_8);
        JsonReader reader = Json.createReader(utf8Reader);

        JsonObject queryResponse = reader.readObject();
        JsonArray workItemsArray = queryResponse.getJsonArray("workItems");

        // Check if done
        if (workItemsArray.isEmpty() && start > 100000)
          break;

        for (JsonValue workItemId : workItemsArray) {
          int id = workItemId.asJsonObject().getInt("id");
          workItemIds.add(id);
        }

        start += batchSize;
      }
      catch (IOException exception) {
        errorStream = connection.getErrorStream();
        String error = Util.getError(errorStream);
        logger_.log(Level.WARNING, "Unable to get IDs: " + error, exception);
      }
      finally {
        Util.close(responseStream);
        Util.close(connection);
      }
    }

    return workItemIds;
  }

  /**
   * Pull all work items from the Azure data base.
   *
   * @return  List of all work items. Never null.
   */
  private List<AzureWorkItem> pullWorkItems()
  {
    List<AzureWorkItem> workItems = new ArrayList<>();

    List<Integer> workItemIds = pullAllWorkItemIds();

    int start = 0;
    int batchSize = 50;

    while (true) {
      // Create CSV of items we are retrieving
      StringBuilder s = new StringBuilder();
      for (int i = start; i < Math.min(workItemIds.size(), start + batchSize); i++) {
        if (i > start)
          s.append(",");
        s.append(workItemIds.get(i));
      }
      String ids = s.toString();

      if (ids.length() == 0)
        return workItems;

      System.out.println("Pulling Azure work items " + start + " to " + (start + batchSize - 1) + "...");

      start += batchSize;

      String urlString = BASE_URL + "/_apis/wit/workitems?ids=" + ids + "&$expand=all&api-version=" + API_VERSION;
      URL url = Util.newUrl(urlString);

      HttpURLConnection connection = null;
      InputStream responseStream = null;
      InputStream errorStream = null;

      try {
        connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", AUTHORIZATION_HEADER);
        connection.setRequestProperty("Accept", "application/json");

        responseStream = connection.getInputStream();
        InputStreamReader utf8Reader = new InputStreamReader(responseStream, StandardCharsets.UTF_8);
        JsonReader reader = Json.createReader(utf8Reader);
        JsonObject response = reader.readObject();
        reader.close();

        JsonArray items = response.getJsonArray("value");

        for (JsonValue value : items) {
          JsonObject json = value.asJsonObject();
          AzureWorkItem workItem = new AzureWorkItem(json);
          workItems.add(workItem);
        }
      }
      catch (IOException exception) {
        errorStream = connection.getErrorStream();
        System.out.println(Util.getError(errorStream));

        logger_.log(Level.WARNING, "Error while pulling Azure DevOps issues", exception);
      }
      finally {
        Util.close(errorStream);
        Util.close(responseStream);
        Util.close(connection);
      }
    }
  }

  /**
   * Pull a specific work item from the back-end.
   *
   * @param workItemId  ID of the work item to get. Non-null.
   * @return            The requested work item, or null if not found.
   */
  private AzureWorkItem pullWorkItem(String id)
  {
    String urlString = BASE_URL + "/_apis/wit/workitems/" + id + "?$expand=all&api-version=" + API_VERSION;
    URL url = Util.newUrl(urlString);

    HttpURLConnection connection = null;
    InputStream responseStream = null;
    InputStream errorStream = null;

    try {
      connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("GET");
      connection.setRequestProperty("Authorization", AUTHORIZATION_HEADER);
      connection.setRequestProperty("Accept", "application/json");

      responseStream = connection.getInputStream();
      InputStreamReader utf8Reader = new InputStreamReader(responseStream, StandardCharsets.UTF_8);
      JsonReader reader = Json.createReader(utf8Reader);
      JsonObject response = reader.readObject();
      reader.close();

      // System.out.println(Util.toPretty(response));

      AzureWorkItem workItem = new AzureWorkItem(response);
      return workItem;
    }
    catch (IOException exception) {
      errorStream = connection.getErrorStream();
      logger_.log(Level.WARNING, "Error while pulling Azure DevOps issues: \n" + Util.getError(errorStream), exception);
      return null;
    }
    finally {
      Util.close(errorStream);
      Util.close(responseStream);
      Util.close(connection);
    }
  }

  /**
   * Destroy all work items in the back-end.
   */
  public void destroyAllWorkItems()
  {
    List<Integer> workItemIds = pullAllWorkItemIds();

    for (Integer workItemId : workItemIds) {
      String urlString = BASE_URL + "/_apis/wit/workitems/" + workItemId + "?destroy=true&api-version=" + API_VERSION;
      URL url = Util.newUrl(urlString);

      HttpURLConnection connection = null;

      try {
        connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("DELETE");
        connection.setRequestProperty("Authorization", AUTHORIZATION_HEADER);
        connection.setRequestProperty("Accept", "application/json");

        int responseCode = connection.getResponseCode();
        if (responseCode == 204) {
          logger_.log(Level.INFO, "Work item " + workItemId + " has been destroyed.");
        }
        else {
          logger_.log(Level.WARNING, "Unable to detroy " + workItemId + ": " + responseCode);
        }
      }
      catch (IOException exception) {
        logger_.log(Level.WARNING, "Unable to detroy " + workItemId);
      }
      finally  {
        Util.close(connection);
      }
    }
  }

  /**
   * For debugging.
   */
  private void listFields()
  {
    String urlString = BASE_URL + "/_apis/wit/fields?api-version=" + API_VERSION;
    URL url = Util.newUrl(urlString);

    InputStream inputStream = null;

    try {
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("GET");
      connection.setRequestProperty("Authorization", AUTHORIZATION_HEADER);
      connection.setRequestProperty("Accept", "application/json");

      inputStream = connection.getInputStream();

      JsonReader reader = Json.createReader(inputStream);
      JsonObject jsonObject = reader.readObject();

      System.out.println(Util.toPretty(jsonObject));
    }
    catch (IOException exception) {
      logger_.log(Level.WARNING, "Listing Azure fields failed", exception);
    }
    finally {
      Util.close(inputStream);
    }
  }

  /**
   * Get all work items available. Download from back-end on first access.
   *
   * @return  All work items from the back-end. Never null.
   */
  public List<AzureWorkItem> getWorkItems()
  {
    if (workItems_.isEmpty()) {
      workItems_.addAll(pullWorkItems());
      resolveParentLinks();
      resolveLinks();
    }

    return Collections.unmodifiableList(workItems_);
  }

  /**
   * Create a mapping as a JSON object string in the form:
   *
   * <pre>
   * {
   *   "<originalID>": "<newID>",
   *   "<originalID>": "<newID>",
   *   :
   * }
   * </pre>
   *
   * The logic relies on the fact that we include the original issue ID
   * in the title of the Azure work item. The algorithm is to pull all
   * work items in the database and then extract the original issue ID
   * from the title of each.
   * <p>
   * The method can be used for issues originating from both Jira and YouTrack.
   *
   * @return  Mapping from original to new issue ID. Never null.
   */
  public String getTicketMapping()
  {
    List<AzureWorkItem> workItems = getWorkItems();

    StringBuilder s = new StringBuilder();
    s.append("{");
    for (int i = 0; i < workItems.size(); i++) {
      AzureWorkItem workItem = workItems.get(i);

      if (i > 0)
        s.append(",\n");

      int workItemId = workItem.getId();
      String originalId = workItem.getTitle().split(" ")[0];

      s.append("  \"" + originalId + "\": \"" + workItemId + "\"");
    }

    s.append("\n}");
    return s.toString();
  }

  public static void main(String[] arguments)
  {
    Azure azure = new Azure();
    String s = azure.getTicketMapping();
    System.out.println(s);
  }
}
