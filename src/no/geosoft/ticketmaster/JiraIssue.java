package no.geosoft.ticketmaster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

/**
 * A class modelling the properties of a Jira ticket.
 *
 * @author <a href="mailto:jacob.dreyer@geosoft.no">Jacob Dreyer</a>
 */
public final class JiraIssue
{
  public enum Type
  {
    EPIC("Epic"),
    STORY("Story"),
    BUG("Bug"),
    SUBTASK("Sub-task"),
    TASK("Task");

    private final String tag_;

    private Type(String tag)
    {
      tag_ = tag;
    }

    private static Type get(String tag)
    {
      for (Type type : Type.values())
        if (type.tag_.equals(tag))
          return type;

      assert false : "Missing type: " + tag;
      return null;
    }

    @Override
    public String toString()
    {
      return tag_;
    }
  }

  public enum Category
  {
    BACKEND("Backend");

    private final String tag_;

    private Category(String tag)
    {
      tag_ = tag;
    }

    private static Category get(String tag)
    {
      for (Category category : Category.values())
        if (category.tag_.equals(tag))
          return category;

      assert false : "Missing category: " + tag;
      return null;
    }

    @Override
    public String toString()
    {
      return tag_;
    }
  }

  public enum Priority
  {
    CRITICAL("Critical"),
    MAJOR("Major"),
    NORMAL("Normal"),
    MINOR("Minor"),
    UNDEFINED("Undefined");

    private final String tag_;

    private Priority(String tag)
    {
      tag_ = tag;
    }

    private static Priority get(String tag)
    {
      for (Priority priority : Priority.values())
        if (priority.tag_.equals(tag))
          return priority;

      assert false : "Missing priority: " + tag;
      return null;
    }

    @Override
    public String toString()
    {
      return tag_;
    }
  }

  public enum Status
  {
    BACKLOG("Backlog"),
    TODO("To Do"),
    IN_PROGRESS("In Progress"),
    CODE_REVIEW("Code Review"),
    READY_FOR_TEST("Ready for Test"),
    TEST("Test"),
    PO_REVIEW("PO Review"),
    BLOCKED("Blocked"),
    DONE("Done");

    private final String tag_;

    private Status(String tag)
    {
      tag_ = tag;
    }

    private static Status get(String tag)
    {
      for (Status status : Status.values())
        if (status.tag_.equals(tag))
          return status;

      assert false : "Missing status: " + tag;
      return null;
    }

    @Override
    public String toString()
    {
      return tag_;
    }
  }

  /** The readable issue ID. */
  private final String id_;

  /** The back-end issue key. */
  private final String key_;

  /** Issue type. */
  private final Type type_;

  /** Issue category. */
  private final Category category_;

  /** The creator of the issue. */
  private final JiraUser creator_;

  /** Time created. Null? */
  private final Date createdTime_;

  /** The original reporter. Null? */
  private final JiraUser reporter_;

  /** Current assignee. May be null. */
  private final JiraUser assignee_;

  /** Issue priority. */
  private final Priority priority_;

  /** Issue status. */
  private final Status status_;

  /** Issue summary, i.e. its title. */
  private final String summary_;

  /** Issue description in rich text, typically HTML or Markdown. */
  private final String description_;

  /** Description of how to reproduce. */
  private final String stepsToReproduce_;

  /** Some custom fields. */
  private final String customRootCause_;
  private final String customRootCauseDescription_;
  private final String customExpectedAndActualResults_;
  private final String customAcceptanceCriteria_;
  private final String customNotesResources_;
  private final String customScreensFigma_;
  private final String customDefinitionOfDone_;
  private final String customHighLevelTestCases_;
  private final String customScreensVideosResources_;
  private final String customDevicesAndVersions_;
  private final String customPlatform_;
  private final String customEnv_;

  private final JiraUser customQaAssignee_;

  private final Date customStartDate_;
  private final Date dueDate_;

  /** Links to other Jira issues. */
  private final Set<JiraLink> links_ = new HashSet<>();

  /** All comments made to this issue. */
  private final Set<JiraComment> comments_ = new TreeSet<>();

  /** All associated labels (tags).  */
  private final Set<String> labels_ = new HashSet<>();

  /** ID of parent issue. Identified ate pull time. */
  private final String parentIssueId_;

  /** The actual parent issue. Resolved later. */
  private JiraIssue parentIssue_;

  /** Issue attachments. */
  private final List<JiraAttachment> attachments_ = new ArrayList<>();

  /**
   * Create a Jira issue instance based on the JSON object response from a REST API
   * call to the Jira back-end.
   *
   * @param jsonObject  JSON object from back-end. Non-null.
   * @throws IllegalArgumentException  If jsonObject is null.
   */
  public JiraIssue(JsonObject jsonObject)
  {
    if (jsonObject == null)
      throw new IllegalArgumentException("jsonObject cannot be null");

    // System.out.println(Util.toPretty(jsonObject));

    // id
    id_ = jsonObject.getString("id", null);

    // key
    key_ = jsonObject.getString("key", null);

    JsonObject fieldsObject = jsonObject.getJsonObject("fields");
    JsonObject renderedFieldsObject = jsonObject.getJsonObject("renderedFields");

    // type
    JsonObject typeObject = fieldsObject.getJsonObject("issuetype");
    String typeTag = typeObject.getString("name", null);
    type_ = Type.get(typeTag);

    // creator
    JsonObject creatorObject = fieldsObject.getJsonObject("creator");
    creator_ = new JiraUser(creatorObject);

    // created
    String createdText = fieldsObject.getString("created", null);
    createdTime_ = Util.getTime(createdText);

    // summary
    summary_ = fieldsObject.getString("summary", null);

    // description
    description_ = renderedFieldsObject.getString("description", null);

    // steps to reproduce
    stepsToReproduce_ = renderedFieldsObject.getString("customfield_11719", null);

    // custom stuff
    JsonObject customRootCauseObject = Util.getJsonObject(fieldsObject, "customfield_11653");
    customRootCause_ = customRootCauseObject != null ? customRootCauseObject.getString("value", null) : null;

    customRootCauseDescription_ = fieldsObject.getString("customfield_11656", null); // Text only!
    customExpectedAndActualResults_ = renderedFieldsObject.getString("customfield_11720", null);
    customAcceptanceCriteria_ = renderedFieldsObject.getString("customfield_11696", null);
    customNotesResources_ = renderedFieldsObject.getString("customfield_11702", null);
    customScreensFigma_ = renderedFieldsObject.getString("customfield_11698", null);
    customDefinitionOfDone_ = renderedFieldsObject.getString("customfield_11697", null);
    customHighLevelTestCases_ = renderedFieldsObject.getString("customfield_11988", null);
    customScreensVideosResources_ = renderedFieldsObject.getString("customfield_11721", null);
    customDevicesAndVersions_ = renderedFieldsObject.getString("customfield_11722", null);

    JsonObject customEnvObject = Util.getJsonObject(fieldsObject, "customfield_11724");
    customEnv_ = customEnvObject != null ? customEnvObject.getString("value", null) : null;

    JsonObject customPlatform = Util.getJsonObject(fieldsObject, "customfield_11615");
    customPlatform_ = customPlatform != null ? customPlatform.getString("value", null) : null;

    JsonValue customQaAssignee = fieldsObject.get("customfield_11712");
    customQaAssignee_ = customQaAssignee != null && customQaAssignee.getValueType() == JsonValue.ValueType.OBJECT ? new JiraUser(customQaAssignee.asJsonObject()) : null;

    // custom start date
    String startDateText = fieldsObject.getString("customfield_11601", null);
    customStartDate_ = Util.getTime(startDateText);

    // duedate
    String dueDateText = fieldsObject.getString("duedate", null);
    dueDate_ = Util.getTime(dueDateText);

    // reporter
    reporter_ = new JiraUser(fieldsObject.getJsonObject("reporter"));

    // assignee
    JsonValue assigneeObject = fieldsObject.get("assignee");
    assignee_ = assigneeObject.getValueType() == JsonValue.ValueType.OBJECT ? new JiraUser(assigneeObject.asJsonObject()) : null;

    // priority
    JsonObject priorityObject = fieldsObject.getJsonObject("priority");
    String priorityTag = priorityObject.getString("name");
    priority_ = Priority.get(priorityTag);

    // status
    JsonObject statusObject = fieldsObject.getJsonObject("status");
    String statusTag = statusObject.getString("name", null);
    status_ = Status.get(statusTag);

    // comments
    JsonObject commentsObject = fieldsObject.getJsonObject("comment");
    JsonArray commentsArray = commentsObject.getJsonArray("comments");
    JsonObject renderedCommentsObject = renderedFieldsObject.getJsonObject("comment");
    JsonArray renderedCommentsArray = renderedCommentsObject.getJsonArray("comments");
    for (JsonValue comment : commentsArray) {
      JsonObject commentObject = comment.asJsonObject();
      JsonObject renderedCommentObject = null;

      String commentId = commentObject.getString("id");
      String body = commentObject.getString("body", null);
      for (JsonValue value : renderedCommentsArray) {
        JsonObject object = value.asJsonObject();
        if (object.getString("id").equals(commentId)) {
          renderedCommentObject = object;
          break;
        }
      }

      comments_.add(new JiraComment(commentObject, renderedCommentObject));
    }

    // labels
    JsonArray labelsArray = fieldsObject.getJsonArray("labels");
    if (labelsArray != null) {
      for (int i = 0; i < labelsArray.size(); i++) {
        String label = labelsArray.getString(i);
        labels_.add(label);
      }
    }

    // parent
    JsonObject parentObject = fieldsObject.getJsonObject("parent");
    parentIssueId_ = parentObject != null ? parentObject.getString("id", null) : null;

    // links
    JsonArray linksArray = fieldsObject.getJsonArray("issuelinks");
    for (JsonValue link : linksArray) {
      links_.add(new JiraLink(link.asJsonObject()));
    }

    // attachments
    JsonArray attachmentArray = fieldsObject.getJsonArray("attachment");
    if (attachmentArray != null) {
      for (JsonValue value : attachmentArray) {
        JsonObject attachmentObject = value.asJsonObject();
        JiraAttachment attachment = new JiraAttachment(attachmentObject);
        attachments_.add(attachment);
      }
    }

    // category
    JsonValue categoryValue = fieldsObject.get("customfield_11615");
    JsonObject categoryObject = categoryValue != null && categoryValue.getValueType() == JsonValue.ValueType.OBJECT ? categoryValue.asJsonObject() : null;
    String categoryTag = categoryObject != null ? priorityObject.getString("value", null) : null;
    category_ = categoryTag != null ? Category.get(categoryTag) : null;
  }

  public String getId()
  {
    return id_;
  }

  public String getKey()
  {
    return key_;
  }

  public Type getType()
  {
    return type_;
  }

  public JiraUser getCreator()
  {
    return creator_;
  }

  public Date getCreatedTime()
  {
    return createdTime_;
  }

  public String getSummary()
  {
    return summary_;
  }

  public String getDescription()
  {
    return description_;
  }

  public String getStepsToReproduce()
  {
    return stepsToReproduce_;
  }

  public String getCustomRootCause()
  {
    return customRootCause_;
  }

  public String getCustomRootCauseDescription()
  {
    return customRootCauseDescription_;
  }

  public String getCustomExpectedAndActualResults()
  {
    return customExpectedAndActualResults_;
  }

  public String getCustomAcceptanceCriteria()
  {
    return customAcceptanceCriteria_;
  }

  public String getCustomNotesResources()
  {
    return customNotesResources_;
  }

  public String getCustomScreensFigma()
  {
    return customScreensFigma_;
  }

  public String getCustomDefinitionOfDone()
  {
    return customDefinitionOfDone_;
  }

  public String getCustomHighLevelTestCases()
  {
    return customHighLevelTestCases_;
  }

  public String getCustomScreensVideosResources()
  {
    return customScreensVideosResources_;
  }

  public String getCustomDevicesAndVersions()
  {
    return customDevicesAndVersions_;
  }

  public String getCustomPlatform()
  {
    return customPlatform_;
  }

  public JiraUser getCustomQaAssignee()
  {
    return customQaAssignee_;
  }

  public Date getCustomStartDate()
  {
    return customStartDate_ != null ? new Date(customStartDate_.getTime()) : null;
  }

  public Date getDueDate()
  {
    return dueDate_ != null ? new Date(dueDate_.getTime()) : null;
  }

  public String getCustomEnv()
  {
    return customEnv_;
  }

  public JiraUser getReporter()
  {
    return reporter_;
  }

  public JiraUser getAssignee()
  {
    return assignee_;
  }

  public Priority getPriority()
  {
    return priority_;
  }

  public Status getStatus()
  {
    return status_;
  }

  public String getParentIssueId()
  {
    return parentIssueId_;
  }

  public JiraIssue getParentIssue()
  {
    return parentIssue_;
  }

  public void setParentIssue(JiraIssue parentIssue)
  {
    parentIssue_ = parentIssue;
  }

  public Set<JiraLink> getLinks()
  {
    return Collections.unmodifiableSet(links_);
  }

  public Set<JiraComment> getComments()
  {
    return Collections.unmodifiableSet(comments_);
  }

  public List<JiraAttachment> getAttachments()
  {
    return Collections.unmodifiableList(attachments_);
  }

  public Set<String> getLabels()
  {
    return Collections.unmodifiableSet(labels_);
  }

  @Override
  public String toString()
  {
    StringBuilder s = new StringBuilder();
    s.append(key_ + " " + summary_ + "\n");
    s.append("  Type..........: " + type_ + "\n");
    s.append("  Category......: " + category_ + "\n");
    s.append("  Creator.......: " + creator_ + "\n");
    s.append("  Description...: " + description_ + "\n");
    s.append("  Priority......: " + priority_ + "\n");
    s.append("  Status........: " + status_ + "\n");
    s.append("  Parent........: " + parentIssueId_ + "\n");

    if (!labels_.isEmpty()) {
      s.append("  Labels........: ");
      for (String label : labels_)
        s.append(label + ",");
      s.append("\n");
    }

    if (!comments_.isEmpty()) {
      s.append("  Comments: \n");
      for (JiraComment comment : comments_) {
        s.append("    " + comment + "\n");
      }
    }

    if (!attachments_.isEmpty()) {
      s.append("  Attachments: \n");
      for (JiraAttachment attachment : attachments_) {
        s.append("    " + attachment + "\n");
      }
    }

    return s.toString();
  }
}
