package no.geosoft.ticketmaster;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
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

/**
 * Class representing a Jira instance.
 *
 * @author <a href="mailto:jacob.dreyer@geosoft.no">Jacob Dreyer</a>
 */
public final class Jira
{
  /** The logger instance. */
  private static final Logger logger_ = Logger.getLogger(Jira.class.getName());

  /** Jira instance base URL. */
  private static final String BASE_URL = "https://<organization>.atlassian.net/";

  /** Email for authentication. */
  private static final String EMAIL = "<user email>";

  /** Access token for the user above. */
  private static final String API_TOKEN = "<PAT>";

  /** Name of Jira project we are accessing. */
  private static final String PROJECT = "<project>";

  /** HTTP authorization header. */
  private static final String AUTHORIZATION_HEADER = "Basic " + Base64.getEncoder().encodeToString((EMAIL + ":" + API_TOKEN).getBytes(StandardCharsets.UTF_8));

  /** Issue fields we are requesting. One per line for readability. */
  private static final String ISSUE_FIELDS_RAW = """
                                                 summary,
                                                 description,
                                                 status,
                                                 issuetype,
                                                 priority,
                                                 reporter,
                                                 assignee,
                                                 created,
                                                 updated,
                                                 resolution,
                                                 labels,
                                                 components,
                                                 fixVersions,
                                                 resolutiondate,
                                                 duedate,
                                                 creator,
                                                 comment,
                                                 parent,
                                                 issuelinks,
                                                 attachment,
                                                 project,
                                                 customfield_11653,
                                                 customfield_11656,
                                                 customfield_11719,
                                                 customfield_11712,
                                                 customfield_11720,
                                                 customfield_11696,
                                                 customfield_11702,
                                                 customfield_11698,
                                                 customfield_11697,
                                                 customfield_11988,
                                                 customfield_11721,
                                                 customfield_11722,
                                                 customfield_11615,
                                                 customfield_11601,
                                                 customfield_11724
                                                 """;

  /** The actual fields list we are passing into Jira through URL. */
  private static final String ISSUE_FIELDS = ISSUE_FIELDS_RAW.replaceAll("\\r?\\n", "");

  /** All issues loaded. */
  private final List<JiraIssue> issues_ = new ArrayList<>();

  /**
   * Create an instance <em>representing</em> the Jira back-end.
   */
  public Jira()
  {
    // Nothing
  }

  private void resolveParentLinks()
  {
    for (JiraIssue issue : issues_) {
      String parentIssueId = issue.getParentIssueId();
      if (parentIssueId == null)
        continue;

      JiraIssue parentIssue = findIssueById(parentIssueId);
      if (parentIssue == null) {
        logger_.log(Level.WARNING, "Unable to find parent issue of " + issue.getKey() + ": " + parentIssueId);
        continue;
      }

      issue.setParentIssue(parentIssue);
    }
  }

  private void resolveLinks()
  {
    for (JiraIssue issue : issues_) {
      for (JiraLink link : issue.getLinks()) {
        String linkedIssueId = link.getLinkedIssueId();
        if (linkedIssueId == null)
          continue;

        JiraIssue linkedIssue = findIssueById(linkedIssueId);
        if (linkedIssue == null) {
          logger_.log(Level.WARNING, "Unexpected: Linked issue does not exist: " + linkedIssueId);
          continue;
        }

        logger_.log(Level.INFO, "Update Jira link " + issue.getId() + " -> " + linkedIssue.getId() + " (" + link.getType() + ")");
        link.setLinkedIssue(linkedIssue);
      }
    }
  }

  /**
   * Find the specified issue from the ones already loaded.
   *
   * @param id  ID of issue to find. Non-null.
   * @return    The requested issue, or null if not found or not yet loaded.
   */
  private JiraIssue findIssueById(String id)
  {
    assert id != null : "id cannot be null";

    for (JiraIssue issue : issues_) {
      if (issue.getId().equals(id))
        return issue;
    }

    // Not found
    return null;
  }

  /**
   * Find the specified issue from the ones already loaded.
   *
   * @param key  Key of issue to find. Non-null.
   * @return     The requested issue, or null if not found or not yet loaded.
   */
  private JiraIssue findIssue(String key)
  {
    assert key != null : "key cannot be null";

    for (JiraIssue issue : issues_) {
      if (issue.getKey().equals(key))
        return issue;
    }

    // Not found
    return null;
  }

  /**
   * Pull the content of the specified attachment.
   *
   * @param attachment  Attachment to pull content of. Non-null.
   * @return            The attachment binary content, or null if not found.
   */
  private byte[] pullAttachment(JiraAttachment attachment)
  {
    assert attachment != null : "attachment cannot be null";

    URL url = Util.newUrl(attachment.getUrl());

    HttpURLConnection connection = null;
    InputStream inputStream = null;

    try {
      connection = (HttpURLConnection) url.openConnection();
      connection.setRequestProperty("Authorization", AUTHORIZATION_HEADER);
      connection.setRequestMethod("GET");

      inputStream = connection.getInputStream();
      ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();

      logger_.log(Level.INFO, "Pulling attachment: " + attachment.getFileName());

      byte[] data = new byte[4096];
      int nBytesRead;
      while ((nBytesRead = inputStream.read(data)) != -1)
        byteBuffer.write(data, 0, nBytesRead);

      byte[] bytes = byteBuffer.toByteArray();

      logger_.log(Level.INFO, "Attachment pulled, size=" + bytes.length);

      return bytes;
    }
    catch (IOException exception) {
      logger_.log(Level.WARNING, "Unable to capture attachment: " + attachment, exception);
      return null;
    }
    finally  {
      Util.close(inputStream);
      Util.close(connection);
    }
  }

  /**
   * Pull a specific issue from the back-end.
   *
   * @param key  Key of issue to pull. Non-null.
   * @return     The requested issue or null if not found.
   */
  private JiraIssue pullIssue(String key)
  {
    assert key != null : "key cannot be null";

    String urlString = BASE_URL + "/rest/api/2/issue/" + key + "?fields=" + ISSUE_FIELDS + "&expand=renderedFields,comments";
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
      reader = Json.createReader(utf8Reader);
      JsonObject issueJson = reader.readObject();

      // System.out.println(Util.toPretty(issueJson));

      return new JiraIssue(issueJson);
    }
    catch (IOException exception) {
      logger_.log(Level.WARNING, "Unable to get issue " + key, exception);
      return null;
    }
    finally {
      Util.close(reader);
      Util.close(responseStream);
      Util.close(connection);
    }
  }

  /**
   * Pull all issues from the Jira database.
   *
   * @param nMax  For testing: Pull at most this many issues. -1 for all.
   * @return      The pulled issues. Never null.
   */
  private List<JiraIssue> pullIssues(int nMax)
  {
    List<JiraIssue> issues = new ArrayList<>();

    // Pull 50 issues at the time
    int startAt = 0;
    int maxResults = 50;
    int total = Integer.MAX_VALUE;

    while (startAt < total) {
      String urlString = BASE_URL + "/rest/api/2/search?jql=project=" + PROJECT +
                         "&fields=" + ISSUE_FIELDS +
                         "&startAt=" + startAt +
                         "&maxResults=" + maxResults +
                         "&expand=renderedFields";

      System.out.println("Pulling Jira issues " + startAt + " to " + (startAt + maxResults - 1) + "...");

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
        reader = Json.createReader(utf8Reader);
        JsonObject json = reader.readObject();
        reader.close();

        total = json.getInt("total");

        JsonArray issuesJson = json.getJsonArray("issues");

        for (JsonValue value : issuesJson) {
          JsonObject issueJson = value.asJsonObject();

          JiraIssue issue = new JiraIssue(issueJson);
          issues.add(issue);
        }

        startAt += maxResults;

        if (issues.size() >= nMax)
          return issues.subList(0, nMax);
      }
      catch (IOException exception) {
        logger_.log(Level.WARNING, "Error while extracting issues", exception);
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
   * @param issue  Issue to download attachments of. Non-null.
   * @throws IllegalArgumentException  If issue is null.
   */
  public void pullAttachments(JiraIssue issue)
  {
    if (issue == null)
      throw new IllegalArgumentException("issue cannot be null");

    for (JiraAttachment attachment : issue.getAttachments()) {
      byte[] content = pullAttachment(attachment);
      attachment.setContent(content);
    }
  }

  /**
   * For debugging.
   */
  private void listFields()
  {
    String urlString = BASE_URL + "/rest/api/3/field";
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
      reader = Json.createReader(responseStream);
      JsonArray json = reader.readArray();
      reader.close();

      System.out.println(Util.toPretty(json));
    }
    catch (IOException exception) {
      logger_.log(Level.WARNING, "Listing fields failed", exception);
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
  public List<JiraIssue> getIssues(int nMax)
  {
    // Lazy loaded
    if (issues_.isEmpty()) {
      issues_.addAll(pullIssues(nMax <= 0 ? Integer.MAX_VALUE : nMax));
      resolveParentLinks();
      resolveLinks();
    }

    return Collections.unmodifiableList(issues_);
  }

  /**
   * Return Jira issue of the specified key. Dowload on first access.
   *
   * @param key  Key of issue to get. Non-null.
   * @return     Requested issue, or null if not found.
   * @throws IllegalArgumentException  If key is null.
   */
  public JiraIssue getIssue(String key)
  {
    if (key == null)
      throw new IllegalArgumentException("key cannot be null");

    JiraIssue issue = findIssue(key);
    if (issue == null) {
      issue = pullIssue(key);
      if (issue != null) {
        issues_.add(issue);
        resolveParentLinks();
        resolveLinks();
      }
    }

    return issue;
  }

  /**
   * Get the specified issues. Download on first access.
   *
   * @param keys  Keys of issues to get. Non-null.
   * @return      Requested issues. Never null.
   * @throws IllegalArgumentException  If keys is null.
   */
  public List<JiraIssue> getIssues(String... keys)
  {
    if (keys == null)
      throw new IllegalArgumentException("keys cannot be null");

    List<JiraIssue> issues = new ArrayList<>();
    for (String key : keys) {
      JiraIssue issue = getIssue(key);
      if (issue != null)
        issues.add(issue);
    }

    return issues;
  }

  /**
   * Get all Jira issues. Download on first access.
   *
   * @return  All issues in the Jira store. Never null.
   */
  public List<JiraIssue> getIssues()
  {
    return getIssues(Integer.MAX_VALUE);
  }

  /**
   * Testing the Jire class.
   *
   * @param arguments  Application arguments. Not used.
   */
  public static void main(String[] arguments)
  {
    Jira jira = new Jira();
    JiraIssue issue = jira.pullIssue("SC-1267");
    System.out.println(issue);
  }
}
