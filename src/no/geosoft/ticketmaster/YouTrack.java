package no.geosoft.ticketmaster;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.json.stream.JsonParsingException;

public final class YouTrack
{
  /** The logger instance. */
  private static final Logger logger_ = Logger.getLogger(YouTrack.class.getName());

  /** YouTrack base URL. */
  private static final String BASE_URL = "<url>";

  /**
   * Personal access token on you track. Used as credentials for accessing the REST API.
   */
  private static final String TOKEN = "<PAT>";

  /** HTTP authorization header. */
  private static final String AUTHORIZATION_HEADER = "Bearer " + TOKEN;

  /** Fields to export. See https://www.jetbrains.com/help/youtrack/devportal/api-entity-Issue.html */
  private static final String ISSUE_FIELDS_RAW = """
                                                 id,
                                                 idReadable,
                                                 summary,
                                                 description,
                                                 attachments(name,url,size,extension),
                                                 comments(id,attachments(name,url,size,extension),author(id,login,fullName,email),created,deleted,textPreview,updated),
                                                 created,
                                                 customFields(id,name,value(name,id),projectCustomField(id,field(id,name))),
                                                 links(id,direction,linkType(id,name),issues(id)),
                                                 parent(issues(id)),
                                                 project(id,name),
                                                 reporter(id,login,fullName,email),
                                                 resolved,
                                                 subtasks(id,issues(id,idReadable)),
                                                 tags(id,color(background,foreground),name),
                                                 updated,
                                                 updater(id,login,fullName,email),
                                                 wikifiedDescription
                                                 """;

  private static final String ISSUE_FIELDS = ISSUE_FIELDS_RAW.replaceAll("\\r?\\n", "");

  /** All users from the back-end system. */
  private final Set<YouTrackUser> users_ = new HashSet<>();

  /** All issues from the back-end system. */
  private final List<YouTrackIssue> issues_ = new ArrayList<>();

  /**
   * Create an instance <em>representing</em> the YouTrack back-end.
   */
  public YouTrack()
  {
    // Nothing
  }

  private void resolveParentLinks()
  {
    for (YouTrackIssue issue : issues_) {
      String parentIssueId = issue.getParentIssueId();
      if (parentIssueId == null)
        continue;

      YouTrackIssue parentIssue = findIssueById(parentIssueId);
      if (parentIssue == null) {
        logger_.log(Level.WARNING, "Unexpected: Parent does not exist: " + parentIssueId);
        continue;
      }

      logger_.log(Level.INFO, "Update YouTrack link " + issue.getIdReadable() + " -> " + parentIssue.getIdReadable() + " (parent)");
      issue.setParentIssue(parentIssue);
    }
  }

  private void resolveLinks()
  {
    for (YouTrackIssue issue : issues_) {
      for (YouTrackLink link : issue.getLinks()) {
        String linkedIssueId = link.getLinkedIssueId();
        if (linkedIssueId == null)
          continue;

        YouTrackIssue linkedIssue = findIssueById(linkedIssueId);
        if (linkedIssue == null) {
          logger_.log(Level.WARNING, "Unexpected: Linked issue does not exist: " + linkedIssueId);
          continue;
        }

        logger_.log(Level.INFO, "Update YouTrack link " + issue.getIdReadable() + " -> " + linkedIssue.getIdReadable() + " (" + link.getType() + ")");
        link.setLinkedIssue(linkedIssue);
      }
    }
  }

  private YouTrackIssue findIssue(String idReadable)
  {
    assert idReadable != null : "idReadable cannot be null";

    for (YouTrackIssue issue : issues_) {
      if (issue.getIdReadable().equals(idReadable))
        return issue;
    }

    // Not found
    return null;
  }

  /**
   * Find the specified issue from the ones already loaded.
   *
   * @param id  ID of issue to find. Non-null.
   * @return    The requested issue, or null if not found or not yet loaded.
   */
  private YouTrackIssue findIssueById(String id)
  {
    assert id != null : "id cannot be null";

    for (YouTrackIssue issue : issues_) {
      if (issue.getId().equals(id))
        return issue;
    }

    // Not found
    return null;
  }

  public YouTrackUser findUser(String id)
  {
    for (YouTrackUser user : users_) {
      if (user.getId().equals(id))
        return user;
    }

    // Not Found
    return null;
  }

  /**
   * Pull the attachment at the specified URL and return as an array of bytes.
   *
   * @param attachmentUrl  URL to download attachment from. Non-null.
   * @return               Content of URL as a n array of bytes. Never null.
   * @throws IOExceptiob  If attachmentUrl is null.
   */
  private byte[] pullAttachment(YouTrackAttachment attachment)
  {
    assert attachment != null : "attachment cannot be null";

    String urlString = BASE_URL + attachment.getUrl();
    URL url = Util.newUrl(urlString);

    HttpURLConnection connection = null;
    InputStream inputStream = null;

    try {
      connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("GET");
      connection.setRequestProperty("Authorization", AUTHORIZATION_HEADER);

      inputStream = connection.getInputStream();
      byte[] bytes = inputStream.readAllBytes();

      logger_.log(Level.INFO, "Attachment pulled successfully: " + bytes.length + " bytes");

      return bytes;
    }
    catch (IOException exception) {
      logger_.log(Level.WARNING, "Attachment pull failed", exception);
      return null;
    }
    finally {
      Util.close(inputStream);
      Util.close(connection);
    }
  }


  public Set<YouTrackUser> pullUsers()
  {
    Set<YouTrackUser> users = new HashSet<>();

    int top = 20;
    int skip = 0;
    boolean moreData = true;

    while (true) {
      String urlString = BASE_URL + "/api/users?fields=id,login,name,email&$top=" + top + "&$skip=" + skip;

      logger_.log(Level.INFO, "Pulling YouTrack users " + skip + " to " + (skip + top - 1) + "...");

      URL url = Util.newUrl(urlString);

      HttpURLConnection connection = null;
      InputStream inputStream = null;
      JsonReader reader = null;

      try {
        connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", AUTHORIZATION_HEADER);
        connection.setRequestProperty("Accept", "application/json");

        inputStream = connection.getInputStream();
        reader = Json.createReader(inputStream);
        JsonArray jsonArray = reader.readArray();

        if (jsonArray.isEmpty())
          break;

        for (JsonValue value : jsonArray) {
          JsonObject jsonObject = value.asJsonObject();
          YouTrackUser user = new YouTrackUser(jsonObject);
          users.add(user);
        }

        skip += jsonArray.size();
      }
      catch (IOException exception) {
        logger_.log(Level.WARNING, "User pull failed", exception);
        return null;
      }
      finally {
        Util.close(reader);
        Util.close(inputStream);
        Util.close(connection);
      }
    }

    logger_.log(Level.INFO, users.size() + " YouTrack users read successfully");
    return users;
  }

  /**
   * Pull YouTrack user from the specified YouTrack user ID.
   *
   * @param id  User ID to get YouTrack user of. Non-null.
   * @return    Corresponding YouiTrack user, or null if not found or an error occurred.
   */
  private YouTrackUser pullUser(String userId)
  {
    String urlString = BASE_URL + "/api/users/" + userId + "?fields=id,login,email,name";
    URL url = Util.newUrl(urlString);

    HttpURLConnection connection = null;
    InputStream inputStream = null;
    JsonReader reader = null;

    try {
      connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("GET");
      connection.setRequestProperty("Authorization", AUTHORIZATION_HEADER);
      connection.setRequestProperty("Accept", "application/json");

      inputStream = connection.getInputStream();
      reader = Json.createReader(inputStream);
      JsonObject jsonObject = reader.readObject();

      YouTrackUser user = new YouTrackUser(jsonObject);

      logger_.log(Level.INFO, "YouTrack user read successfully: " + user);

      return user;
    }
    catch (IOException exception) {
      logger_.log(Level.WARNING, "User pull failed", exception);
      return null;
    }
    finally {
      Util.close(reader);
      Util.close(inputStream);
      Util.close(connection);
    }
  }

  /**
   * Pull a specific issue from database.
   *
   * @param id  ID (Database or readable) of issue to pull. Non-null.
   * @return    The requested issue, or null if not found.
   */
  private YouTrackIssue pullIssue(String id)
  {
    assert id != null : "id cannot be null";

    String urlString = BASE_URL + "/api/issues/" + id + "?fields=" + ISSUE_FIELDS;
    URL url = Util.newUrl(urlString);

    HttpURLConnection connection = null;
    InputStream responseStream = null;
    JsonReader reader = null;

    try {
      connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("GET");
      connection.setRequestProperty("Authorization", AUTHORIZATION_HEADER);
      connection.setRequestProperty("Accept", "application/json");

      responseStream = connection.getInputStream();
      InputStreamReader utf8Reader = new InputStreamReader(responseStream, StandardCharsets.UTF_8);
      reader = Json.createReader(responseStream);

      JsonObject issueJson = reader.readObject();

      // System.out.println(Util.toPretty(issueJson));

      return new YouTrackIssue(issueJson);
    }
    catch (IOException exception) {
      logger_.log(Level.WARNING, "Unable to extract", exception);
      return null;
    }
    finally {
      Util.close(reader);
      Util.close(responseStream);
      Util.close(connection);
    }
  }

  /**
   * Pull all issues from the YouTrack database.
   *
   * @param nMax  For testing: Pull at most this many issues. -1 for all.
   * @return      The pulled issues. Never null.
   */
  private List<YouTrackIssue> pullIssues(int nMax)
  {
    List<YouTrackIssue> issues = new ArrayList<>();

    // Pull 100 issues at the time
    int top = 100;
    int skip = 0;

    while (true) {
      String urlString = BASE_URL + "/api/issues?fields=" + ISSUE_FIELDS + "&$top=" + top + "&$skip=" + skip;
      URL url = Util.newUrl(urlString);

      logger_.log(Level.INFO, "Pulling YouTrack issues " + skip + " to " + (skip + top - 1) + "...");

      HttpURLConnection connection = null;
      InputStream responseStream = null;
      JsonReader reader = null;

      try {
        connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", AUTHORIZATION_HEADER);
        connection.setRequestProperty("Accept", "application/json");

        responseStream = connection.getInputStream();
        reader = Json.createReader(responseStream);
        JsonArray issuesArray = reader.readArray();
        reader.close();

        if (issuesArray.isEmpty())
          break;

        for (JsonValue value : issuesArray) {
          JsonObject issueJson = value.asJsonObject();

          YouTrackIssue issue = new YouTrackIssue(issueJson);

          // Resolve developer
          String developerId = issue.getCustomDeveloperId();
          YouTrackUser developer = findUser(developerId);
          issue.setDeveloper(developer);

          // Resolve tester
          String testerId = issue.getCustomTesterId();
          YouTrackUser tester = findUser(testerId);
          issue.setTester(tester);

          issues.add(issue);
        }

        skip += issuesArray.size();

        if (issues.size() >= nMax)
          return issues.subList(0, nMax);
      }
      catch (JsonParsingException exception) {
        logger_.log(Level.WARNING, "JSON parsing failed", exception);
        break;
      }
      catch (IOException exception) {
        logger_.log(Level.WARNING, "Unable to extract", exception);
        break;
      }
      finally {
        Util.close(reader);
        Util.close(responseStream);
        Util.close(connection);
      }
    }

    return issues;
  }

  /**
   * Pull all attachments of the specified issue and update their
   * content member accordingly.
   *
   * @param issue  Issue to dowload attachments of. Non-null.
   * @throws IllegalArgumentException  If issue is null.
   */
  public void pullAttachments(YouTrackIssue issue)
  {
    if (issue == null)
      throw new IllegalArgumentException("issue cannot be null");

    for (YouTrackAttachment attachment : issue.getAttachments()) {
      byte[] content = pullAttachment(attachment);
      attachment.setContent(content);
    }
  }

  /**
   * For debugging.
   */
  private void listFields()
  {
    String urlString = BASE_URL + "/api/admin/customFieldSettings/customFields?fields=id,name,fieldType(id,localizedName)";
    URL url = Util.newUrl(urlString);

    HttpURLConnection connection = null;
    InputStream responseStream = null;
    JsonReader reader = null;

    System.out.println(urlString);

    try {
      connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("GET");
      connection.setRequestProperty("Authorization", AUTHORIZATION_HEADER);
      connection.setRequestProperty("Accept", "application/json");

      responseStream = connection.getInputStream();
      InputStreamReader utf8Reader = new InputStreamReader(responseStream, StandardCharsets.UTF_8);
      reader = Json.createReader(utf8Reader);

      JsonValue json = reader.read();

      System.out.println(Util.toPretty(json));
    }
    catch (IOException exception) {
      logger_.log(Level.WARNING, "Unable to list fields", exception);
    }
    finally {
      Util.close(reader);
      Util.close(responseStream);
      Util.close(connection);
    }
  }

  /**
   * Get first few issues. Download on first access.
   *
   * @param nMax  Maximum number of issues to get. Negative for all.
   * @return  The requested iseeus. Never null.
   */
  public List<YouTrackIssue> getIssues(int nMax)
  {
    // Loazy loaded
    if (issues_.isEmpty()) {
      // Cache all users
      users_.addAll(pullUsers());

      // Capture all issues
      issues_.addAll(pullIssues(nMax <= 0 ? Integer.MAX_VALUE : nMax));
      resolveParentLinks();
      resolveLinks();
    }

    return Collections.unmodifiableList(issues_);
  }

  /**
   * Return YouTrack issue of the specified key. Dowload on first access.
   *
   * @param key  ID (database or readable) of issue to get. Non-null.
   * @return     Requested issue, or null if not found.
   * @throws IllegalArgumentException  If id is null.
   */
  public YouTrackIssue getIssue(String id)
  {
    if (id == null)
      throw new IllegalArgumentException("id cannot be null");

    YouTrackIssue issue = findIssue(id);
    if (issue == null) {
      issue = pullIssue(id);
      if (issue != null) {
        issues_.add(issue);
        resolveParentLinks();
        resolveLinks();
      }
    }

    return issue;
  }

  /**
   * Get the specified YouTrack issues. Download on first access.
   *
   * @param ids  IDs (database or readble) of issues to get.
   * @return     The requested issues. Never null.
   * @throws IllegalArgumentException  If ids is null.
   */
  public List<YouTrackIssue> getIssues(String... ids)
  {
    if (ids == null)
      throw new IllegalArgumentException("ids cannot be null");

    List<YouTrackIssue> issues = new ArrayList<>();
    for (String id : ids) {
      YouTrackIssue issue = getIssue(id);
      if (issue != null)
        issues.add(issue);
    }

    return issues;
  }

  /**
   * Get all YouTrack issues. Download on first access.
   *
   * @return  All issues in the YouTrack store. Never null.
   */
  public List<YouTrackIssue> getIssues()
  {
    return getIssues(Integer.MAX_VALUE);
  }

  public static void main(String[] arguments)
  {
    YouTrack youTrack = new YouTrack();
    youTrack.listFields();
  }
}
