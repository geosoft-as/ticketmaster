package no.geosoft.ticketmaster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Date;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.logging.Level;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.json.JsonNumber;

/**
 * Class modelling a YouTrack issue.
 *
 * @author <a href="mailto:jacob.dreyer@geosoft.no">Jacob Dreyer</a>
 */
public final class YouTrackIssue
{
  /** The logger instance. */
  private static final Logger logger_ = Logger.getLogger(YouTrackIssue.class.getName());

  // id
  private final String id_;

  // attachments
  private final List<YouTrackAttachment> attachments_ = new ArrayList<>();

  // comments
  private final Set<YouTrackComment> comments_ = new TreeSet<>();

  // created
  private final Date createdTime_;

  // customFields
  private final String custom<Name1>_;
  private final String custom<Name2>_;

  private YouTrackUser developer_;

  private YouTrackUser tester_;

  // description
  private final String description_;

  // idReadable
  private final String idReadable_;

  // links
  private final List<YouTrackLink> links_ = new ArrayList<>();

  // parent
  private final String parentIssueId_;

  private YouTrackIssue parentIssue_;

  // project
  private final String project_;

  // reporter
  private final YouTrackUser reportedBy_;

  // subtasks
  private final YouTrackLink subtasks_;

  // summary
  private final String summary_;

  // tags
  private final List<String> tags_ = new ArrayList<>();

  // updated
  private final Date updatedTime_;

  // updater
  private final YouTrackUser updatedBy_;

  // wikifiedDescription
  private final String wikifiedDescription_;

  // type
  private final String type_;

  public YouTrackIssue(JsonObject jsonObject)
  {
    // id
    id_ = jsonObject.getString("id", null);

    // attachments
    if (jsonObject.containsKey("attachments") && !jsonObject.isNull("attachments")) {
      JsonArray attachments = jsonObject.getJsonArray("attachments");
      for (JsonValue attachmentValue : attachments) {
        YouTrackAttachment attachment = new YouTrackAttachment(attachmentValue.asJsonObject());
        attachments_.add(attachment);
      }
    }

    // comments
    if (jsonObject.containsKey("comments") && !jsonObject.isNull("comments")) {
      JsonArray comments = jsonObject.getJsonArray("comments");
      for (JsonValue commentValue : comments) {
        YouTrackComment comment = new YouTrackComment(commentValue.asJsonObject());
        comments_.add(comment);
      }
    }

    // created
    JsonNumber createdTime = jsonObject.getJsonNumber("created");
    createdTime_ = createdTime != null ? new Date(createdTime.longValue()) : null;

    String custom<Name> = null;
    String custom<Name> = null;

    // customFields
    if (jsonObject.containsKey("customFields") && !jsonObject.isNull("customFields")) {
      JsonArray customFields = jsonObject.getJsonArray("customFields");
      for (JsonValue customFieldValue : customFields) {
        JsonObject c = (JsonObject) customFieldValue;
        String name = c.getString("name", null);
        JsonValue v = c.get("value");
        String value = v instanceof JsonObject ? ((JsonObject) v).getString("name", null) : null;
        String id = v instanceof JsonObject ? ((JsonObject) v).getString("id", null) : null;

        switch (name) {
          case "<Tag>"               : custom<Name> = value; break;
          case "<Tag>"               : custom<Name> = value; break;
        }
      }
    }

    custom<Name>_ = custom<Name;
    custom<Name>_ = custom<Name;

    // description
    description_ = jsonObject.getString("description", null);

    // idReadable
    idReadable_ = jsonObject.getString("idReadable", null);

    // links
    if (jsonObject.containsKey("links") && !jsonObject.isNull("links")) {
      JsonArray links = jsonObject.getJsonArray("links");
      for (JsonValue linkValue : links) {
        YouTrackLink link = new YouTrackLink(linkValue.asJsonObject());

        // All link types are there even if there is no actual link. Include only real ones.
        if (link.getLinkedIssueId() != null)
          links_.add(link);
      }
    }

    // parent
    String parentIssueId = null;
    JsonObject parent = jsonObject.getJsonObject("parent");
    if (parent != null) {
      JsonArray parentIssues = parent.getJsonArray("issues");
      for (JsonValue parentIssue : parentIssues) {
        parentIssueId = ((JsonObject) parentIssue).getString("id");
        break;
      }
    }
    parentIssueId_ = parentIssueId;

    // project
    JsonObject project = jsonObject.getJsonObject("project");
    project_ = project != null ? project.getString("name", null) : null;

    // reporter
    JsonObject reportedBy = jsonObject.getJsonObject("reporter");
    reportedBy_ = reportedBy != null ? new YouTrackUser(reportedBy) : null;

    // subtasks
    JsonObject subtasks = jsonObject.getJsonObject("subtasks");
    subtasks_ = subtasks != null ? new YouTrackLink(subtasks) : null;

    // summary
    summary_ = jsonObject.getString("summary", null);

    // tags
    if (jsonObject.containsKey("tags")&& !jsonObject.isNull("tags")) {
      JsonArray tags = jsonObject.getJsonArray("tags");
      for (JsonValue tagValue : tags) {
        String tag = tagValue.asJsonObject().getString("name", null);
        tags_.add(tag);
      }
    }

    // Updated
    JsonNumber updatedTime = jsonObject.getJsonNumber("updated");
    updatedTime_ = updatedTime != null ? new Date(updatedTime.longValue()) : null;

    // Updater
    JsonObject updatedBy = jsonObject.getJsonObject("updater");
    updatedBy_ = updatedBy != null ? new YouTrackUser(updatedBy) : null;

    // wikifiedDescription
    wikifiedDescription_ = jsonObject.getString("wikifiedDescription", null);

    // type
    type_ = jsonObject.getString("$type", null);
  }

  public String getId()
  {
    return id_;
  }

  public String getIdReadable()
  {
    return idReadable_;
  }

  public String getType()
  {
    return type_;
  }

  public String getProject()
  {
    return project_;
  }

  public String getSummary()
  {
    return summary_;
  }

  public String getDescription()
  {
    return description_;
  }

  public String getWikifiedDescription()
  {
    return wikifiedDescription_;
  }

  public String getParentIssueId()
  {
    return parentIssueId_;
  }

  public YouTrackIssue getParentIssue()
  {
    return parentIssue_;
  }

  public void setParentIssue(YouTrackIssue parentIssue)
  {
    parentIssue_ = parentIssue;
  }

  public YouTrackUser getReportedBy()
  {
    return reportedBy_;
  }

  public Date getCreatedTime()
  {
    return createdTime_ != null ? new Date(createdTime_.getTime()) : null;
  }

  public YouTrackUser getUpdatedBy()
  {
    return updatedBy_;
  }

  public Date getUpdatedTime()
  {
    return updatedTime_ != null ? new Date(updatedTime_.getTime()) : null;
  }

  public List<String> getTags()
  {
    return Collections.unmodifiableList(tags_);
  }

  public Set<YouTrackComment> getComments()
  {
    return Collections.unmodifiableSet(comments_);
  }

  public List<YouTrackAttachment> getAttachments()
  {
    return Collections.unmodifiableList(attachments_);
  }

  public String getCustom<Name>()
  {
    return custom<Name>_;
  }

  public void setDeveloper(YouTrackUser developer)
  {
    developer_ = developer;
  }

  public YouTrackUser getDeveloper()
  {
    return developer_;
  }

  public List<YouTrackLink> getLinks()
  {
    return Collections.unmodifiableList(links_);
  }

  @Override
  public String toString()
  {
    StringBuilder s = new StringBuilder();
    s.append(idReadable_ + " " + summary_ + "\n");
    s.append("  ID........................: " + id_ + "\n");
    s.append("  ID (readable).............: " + idReadable_ + "\n");
    s.append("  Summary...................: " + summary_ + "\n");
    s.append("  Type......................: " + type_ + "\n");
    s.append("  Parent issue ID...........: " + parentIssueId_ + "\n");
    s.append("  Project...................: " + project_ + "\n");
    s.append("  Created...................: " + createdTime_ + "\n");
    s.append("  Reported by...............: " + reportedBy_ + "\n");
    s.append("  Updated...................: " + updatedTime_ + "\n");
    s.append("  Updated by................: " + updatedBy_ + "\n");
    s.append("  Description (wikified)....: \n");
    s.append(wikifiedDescription_ + "\n");

    s.append("  Comments: \n");
    for (YouTrackComment comment : comments_)
      s.append("    " + comment + "\n");

    s.append("  Attachments: \n");
    for (YouTrackAttachment attachment : attachments_)
      s.append("    " + attachment + "\n");

    s.append("  Links: \n");
    for (YouTrackLink link : links_)
      s.append("    " + link + "\n");

    s.append("  Tags: \n");
    for (String tag : tags_)
      s.append("    " + tag + "\n");

    s.append("  Custom Products: \n");
    for (String product : customProducts_)
      s.append("    " + product + "\n");

    return s.toString();
  }
}
