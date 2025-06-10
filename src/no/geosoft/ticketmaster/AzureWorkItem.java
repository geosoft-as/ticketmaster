package no.geosoft.ticketmaster;

import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

/**
 * Class modelling an Azure work item.
 *
 * @author <a href="mailto:jacob.dreyer@geosoft.no">Jacob Dreyer</a>
 */
public final class AzureWorkItem
{
  /** Work item ID in the Azure database. -1 if the work item has not yet been pushed. */
  private int id_;

  /** Unique ID that can identify the work item also before it is injected in Azure. */
  private final long uniqueId_;

  private final String workItemType_;

  private final String areaPath_;

  private final AzureUser createdBy_;

  private final Date createdTime_;

  private final String title_;

  private String description_;

  private final String assignedToId_;

  private AzureUser assignedTo_;

  private final String testerId_;

  private AzureUser tester_;

  private AzureWorkItem parentWorkItem_;

  private final String state_;

  private final boolean isBlocked_;

  private final String project_;

  private final String product_;

  private final int priority_;

  private final String activity_;

  private final Date startDate_;

  private final Date targetDate_;

  private final String systemInfo_;

  private final String foundIn_;

  private final Set<String> tags_ = new HashSet<>();

  private final Set<AzureAttachment> attachments_ = new HashSet<>();

  private final Set<AzureComment> comments_ = new TreeSet<>();

  private final Set<AzureLink> links_ = new HashSet<>();

  private String stepsToReproduce_;

  private final String targetRelease_;

  private final String plcVersion_;

  private final String targetDtApp_;

  public AzureWorkItem(long uniqueId,
                       String workItemType,
                       String areaPath,
                       AzureUser createdBy,
                       Date createdTime,
                       String title,
                       String description,
                       AzureUser assignedTo,
                       AzureUser tester,
                       AzureWorkItem parentWorkItem,
                       String state,
                       boolean isBlocked,
                       String project,
                       String product,
                       int priority,
                       String activity,
                       Date startDate,
                       Date targetDate,
                       String systemInfo,
                       String foundIn,
                       String stepsToReproduce,
                       String targetRelease,
                       String plcVersion,
                       String targetDtApp)
  {
    id_ = -1; // Not stored yet

    uniqueId_ = uniqueId;
    workItemType_ = workItemType;
    areaPath_ = areaPath;
    createdBy_ = createdBy;
    createdTime_ = new Date(createdTime.getTime());
    title_ = title;
    description_ = description;
    assignedTo_ = assignedTo;
    assignedToId_ = null; // TODO
    tester_ = tester;
    testerId_ = null; // TODO
    parentWorkItem_ = parentWorkItem;
    state_ = state;
    isBlocked_ = isBlocked;
    project_ = project;
    product_ = product;
    priority_ = priority;
    activity_ = activity;
    startDate_ = startDate != null ? new Date(startDate.getTime()) : null;
    targetDate_ = targetDate != null ? new Date(targetDate.getTime()) : null;
    systemInfo_ = systemInfo;
    foundIn_ = foundIn;
    stepsToReproduce_ = stepsToReproduce;
    targetRelease_ = targetRelease;
    plcVersion_ = plcVersion;
    targetDtApp_ = targetDtApp;
  }

  public AzureWorkItem(JsonObject jsonObject)
  {
    // System.out.println(Util.toPretty(jsonObject));

    uniqueId_ = -1L; // Not used for persisted work items

    JsonObject fieldsObject = jsonObject.getJsonObject("fields");
    id_ = fieldsObject.getInt("System.Id");
    workItemType_ = fieldsObject.getString("System.WorkItemType", "Task");

    areaPath_ = fieldsObject.getString("System.AreaPath", null);

    JsonObject createdBy = fieldsObject.getJsonObject("System.CreatedBy");
    createdBy_ = new AzureUser(createdBy);

    String createdTime = fieldsObject.getString("System.CreatedDate", null);
    createdTime_ = Util.getTime(createdTime);

    title_ = fieldsObject.getString("System.Title", null);
    description_ = fieldsObject.getString("System.Description", null);

    JsonObject assignedTo = fieldsObject.getJsonObject("System.AssignedTo");
    assignedTo_ = assignedTo != null ? new AzureUser(assignedTo) : null;
    assignedToId_ = null;

    tester_ = null;
    testerId_ = null;
    parentWorkItem_ = null;

    state_ = fieldsObject.getString("System.State", null);

    isBlocked_ = false;
    project_ = fieldsObject.getString("Custom.Project", null);
    product_ = fieldsObject.getString("Custom.Product", null);

    priority_ = fieldsObject.getInt("Microsoft.VSTS.Common.Priority", 3);
    activity_ = fieldsObject.getString("Microsoft.VSTS.Common.Activity", "Development");

    String startDate = fieldsObject.getString("StartDate", null); // TODO
    startDate_ = Util.getTime(startDate);

    String targetDate = fieldsObject.getString("TargetDate", null); // TODO
    targetDate_ = Util.getTime(targetDate);

    systemInfo_ = fieldsObject.getString("Microsoft.VSTS.TCM.SystemInfo", null);

    foundIn_ = null;

    stepsToReproduce_ = null;

    targetRelease_ = fieldsObject.getString("<custom field>", null);
    plcVersion_ = fieldsObject.getString("<custom field>", null);
    targetDtApp_ = fieldsObject.getString("<custom field>", null);
  }

  public void setId(int id)
  {
    id_ = id;
  }

  public int getId()
  {
    return id_;
  }

  public long getUniqueId()
  {
    return uniqueId_;
  }

  public String getAreaPath()
  {
    return areaPath_;
  }

  public AzureUser getCreatedBy()
  {
    return createdBy_;
  }

  public Date getCreatedTime()
  {
    return new Date(createdTime_.getTime());
  }

  public String getState()
  {
    return state_;
  }

  public int getPriority()
  {
    return priority_;
  }

  public String getActivity()
  {
    return activity_;
  }

  public Date getStartDate()
  {
    return startDate_ != null ? new Date(startDate_.getTime()) : null;
  }

  public Date getTargetDate()
  {
    return targetDate_ != null ? new Date(targetDate_.getTime()) : null;
  }

  public String getWorkItemType()
  {
    return workItemType_;
  }

  public String getTitle()
  {
    return title_;
  }

  public String getDescription()
  {
    return description_;
  }

  public void setDescription(String description)
  {
    description_ = description;
  }

  public String getExtendedDescription()
  {
    StringBuilder s = new StringBuilder();
    s.append("<i>");
    s.append("Created by ");
    s.append("<a href='mailto:" + createdBy_.getEmail() + "'>" + createdBy_.getFullName() + "</a>");
    s.append(" on " + Util.DATE_FORMAT.format(createdTime_) + ":");
    s.append("</i>");
    s.append("<br>");
    if (description_ != null)
      s.append(description_);

    return s.toString();
  }

  public String getAssignedToId()
  {
    return assignedToId_;
  }

  public AzureUser getAssignedTo()
  {
    return assignedTo_;
  }

  public void setAssignedTo(AzureUser assignedTo)
  {
    assignedTo_ = assignedTo;
  }

  public String getTesterId()
  {
    return testerId_;
  }

  public AzureUser getTester()
  {
    return tester_;
  }

  public void setTester(AzureUser tester)
  {
    tester_ = tester;
  }

  public AzureWorkItem getParentWorkItem()
  {
    return parentWorkItem_;
  }

  public void setParentWorkItem(AzureWorkItem parentWorkItem)
  {
    parentWorkItem_ = parentWorkItem;
  }

  public void addAttachment(AzureAttachment attachment)
  {
    attachments_.add(attachment);
  }

  public Set<String> getTags()
  {
    return Collections.unmodifiableSet(tags_);
  }

  public void addTag(String tag)
  {
    tags_.add(tag);
  }

  public String getTagsAsString()
  {
    StringBuilder s = new StringBuilder();
    boolean isFirst = true;
    for (String tag : tags_) {
      s.append(isFirst ? "" : "; ");
      s.append(tag);
      isFirst = false;
    }
    return s.toString();
  }

  public Set<AzureAttachment> getAttachments()
  {
    return Collections.unmodifiableSet(attachments_);
  }

  public Set<AzureComment> getComments()
  {
    return Collections.unmodifiableSet(comments_);
  }

  public void addComment(AzureComment comment)
  {
    comments_.add(comment);
  }

  public void addLink(AzureLink link)
  {
    links_.add(link);
  }

  public Set<AzureLink> getLinks()
  {
    return Collections.unmodifiableSet(links_);
  }

  public boolean isBlocked()
  {
    return isBlocked_;
  }

  public String getProject()
  {
    return project_;
  }

  public String getProduct()
  {
    return product_;
  }

  public String getSystemInfo()
  {
    return systemInfo_;
  }

  public String getFoundIn()
  {
    return foundIn_;
  }

  public String getStepsToReproduce()
  {
    return stepsToReproduce_;
  }

  public void setStepsToReproduce(String stepsToReproduce)
  {
    stepsToReproduce_ = stepsToReproduce;
  }

  public String getTargetRelease()
  {
    return targetRelease_;
  }

  public String getPlcVersion()
  {
    return plcVersion_;
  }

  public String getTargetDtApp()
  {
    return targetDtApp_;
  }

  /** {@inheritDocs} */
  @Override
  public String toString()
  {
    StringBuilder s = new StringBuilder();
    s.append(id_ + " " + title_ + "\n");
    s.append("  Type.......................: " + workItemType_ + "\n");
    s.append("  Description................: \n");
    s.append(description_ + "\n");

    s.append("  Start date.................: " + startDate_ + "\n");
    s.append("  Due date...................: " + targetDate_ + "\n");

    s.append("  Comments...................: \n");
    for (AzureComment comment : comments_)
      s.append("    " + comment + "\n");

    s.append("  Attachments................: \n");
    for (AzureAttachment attachment : attachments_)
      s.append("    " + attachment + "\n");

    return s.toString();
  }
}
