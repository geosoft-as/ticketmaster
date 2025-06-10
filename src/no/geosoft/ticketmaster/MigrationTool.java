package no.geosoft.ticketmaster;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class for migrating issues from Jira and YouTrack to Azure Dev Ops.
 *
 * Some limitations:
 * <ul>
 *   <li>
 *     The original comment author cannot be set as originator for comments
 *     (only the one running the scripot can), so the author information is
 *     embedded in the comment itself.
 *   </li>
 *   <li>
 *     HTML tables doesn't render properly in Azure. Jira tables will look strange
 *     when migrated.
 *   </li>
 *   <li>
 *      There is no embedded video viewer in Azure like in Jira. Videos are available
 *      as attachments, but it might not be apparent where they originate from.
 *      (i.e. description, screen section, or comment).
 *   </li>
 *   <li>
 *     Comment reactions must be pulled in a separae query. We don't do this
 *     as it would complicate the code, and it is anyway of limited importance.
 *   </li>
 *
 * @author <a href="mailto:jacob.dreyer@geosoft.no">Jacob Dreyer</a>
 */
public final class MigrationTool
{
  /** The logger instance. */
  private static final Logger logger_ = Logger.getLogger(MigrationTool.class.getName());

  /** Azure work item unique ID counter used for in-memory work items before they are in Azure. */
  private static long uniqueId_ = System.currentTimeMillis();

  /** The YouTrack instance. */
  private final YouTrack youTrack_ = new YouTrack();

  /** The Jira instance. */
  private final Jira jira_ = new Jira();

  /** The Azure instance. */
  private final Azure azure_ = new Azure();

  /** Existing Azure work items. */
  private final List<AzureWorkItem> existingWorkItems_ = new ArrayList<>();

  /** Mapping equivalent Azure work items to YouTrack issues. */
  private final Map<AzureWorkItem, YouTrackIssue> azureToYouTrack_ = new HashMap<>();

  /** Mapping equivalent YouTrack issues to Azure work items. */
  private final Map<YouTrackIssue, AzureWorkItem> youTrackToAzure_ = new HashMap<>();

  /** Mapping equivalent Azure work items to Jira issues. */
  private final Map<AzureWorkItem, JiraIssue> azureToJira_ = new HashMap<>();

  /** Mapping equivalent Jira issues to Azure work items. */
  private final Map<JiraIssue, AzureWorkItem> jiraToAzure_ = new HashMap<>();

  /** Mapping equivalent Azure attachments to YouTrack attachments. */
  private final Map<AzureAttachment, YouTrackAttachment> youTrackAttachments_ = new HashMap<>();

  /** Mapping equivalent Azure attachments to Jira attachments. */
  private final Map<AzureAttachment, JiraAttachment> jiraAttachments_ = new HashMap<>();

  /**
   * Create a new migration tool instance.
   */
  public MigrationTool()
  {
    // Nothing
  }

  private void resolveYouTrackLinks()
  {
    logger_.log(Level.INFO, "Resolving links");

    //
    // Parent links: TODO: THINK PERHAPS THIS IS AUTO_COVERED BY THE BELOW
    //
    for (Map.Entry<AzureWorkItem, YouTrackIssue> entry : azureToYouTrack_.entrySet()) {
      AzureWorkItem workItem = entry.getKey();
      YouTrackIssue youTrackIssue = entry.getValue();

      YouTrackIssue parentYouTrackIssue = youTrackIssue.getParentIssue();
      if (parentYouTrackIssue == null)
        continue;

      AzureWorkItem parentWorkItem = youTrackToAzure_.get(parentYouTrackIssue);

      logger_.log(Level.INFO, "Resolve link " + workItem.getId() + " -> " + parentWorkItem.getId() + " (parent)");

      workItem.setParentWorkItem(parentWorkItem);
    }

    //
    // General links
    //
    for (Map.Entry<AzureWorkItem, YouTrackIssue> entry : azureToYouTrack_.entrySet()) {
      AzureWorkItem workItem = entry.getKey();
      YouTrackIssue youTrackIssue = entry.getValue();

      for (YouTrackLink link : youTrackIssue.getLinks()) {
        YouTrackIssue linkedYouTrackIssue = link.getLinkedIssue();
        if (linkedYouTrackIssue == null) // TODO: Warning
          continue;

        AzureWorkItem linkedWorkItem = youTrackToAzure_.get(linkedYouTrackIssue);

        logger_.log(Level.INFO, "Resolve link " + workItem.getId() + " -> " + linkedWorkItem.getId() + " " + link.getType());

        workItem.addLink(newAzureLink(linkedWorkItem, link));
      }
    }
  }

  private void resolveJiraLinks()
  {
    logger_.log(Level.INFO, "Resolving Jira links");

    //
    // Parent links
    //
    for (Map.Entry<AzureWorkItem, JiraIssue> entry : azureToJira_.entrySet()) {
      AzureWorkItem workItem = entry.getKey();
      JiraIssue jiraIssue = entry.getValue();

      JiraIssue parentJiraIssue = jiraIssue.getParentIssue();
      if (parentJiraIssue == null)
        continue;

      AzureWorkItem parentWorkItem = jiraToAzure_.get(parentJiraIssue);

      logger_.log(Level.INFO, "Resolve link " + workItem.getId() + " -> " + parentWorkItem.getId() + " (parent)");

      workItem.setParentWorkItem(parentWorkItem);

      workItem.addLink(new AzureLink(AzureLink.Type.SUBTASK, parentWorkItem));
    }

    //
    // General links
    //
    for (Map.Entry<AzureWorkItem, JiraIssue> entry : azureToJira_.entrySet()) {
      AzureWorkItem workItem = entry.getKey();
      JiraIssue jiraIssue = entry.getValue();

      for (JiraLink jiraLink : jiraIssue.getLinks()) {
        JiraIssue linkedJiraIssue = jiraLink.getLinkedIssue();
        if (linkedJiraIssue == null) // TODO: Warning
          continue;

        AzureWorkItem linkedWorkItem = jiraToAzure_.get(linkedJiraIssue);

        logger_.log(Level.INFO, "Resolve link " + workItem.getId() + " -> " + linkedWorkItem.getId() + " " + jiraLink.getType());

        if (jiraLink.getDirection().equals("INWARD"))
          workItem.addLink(newAzureLink(linkedWorkItem, jiraLink));
      }
    }
  }

  private static AzureUser newAzureUser(YouTrackUser youTrackUser)
  {
    String login = youTrackUser.getLogin();
    String fullName = youTrackUser.getFullName();
    String email = youTrackUser.getEmail();

    return new AzureUser(login, fullName, email);
  }

  private static AzureUser newAzureUser(JiraUser jiraUser)
  {
    String login = jiraUser.getLogin();
    String fullName = jiraUser.getName();
    String email = jiraUser.getEmail();

    return new AzureUser(login, fullName, email);
  }

  private static AzureLink newAzureLink(AzureWorkItem linkedWorkItem, YouTrackLink youTrackLink)
  {
    AzureLink.Type type = null;
    if (youTrackLink.getType() == YouTrackLink.Type.RELATES)
      type = AzureLink.Type.RELATED;

    else if (youTrackLink.getType() == YouTrackLink.Type.DEPEND && youTrackLink.getDirection().equals("INWARD"))
      type = AzureLink.Type.DEPENDS_ON;

    else if (youTrackLink.getType() == YouTrackLink.Type.DUPLICATE && youTrackLink.getDirection().equals("INWARD"))
      type = AzureLink.Type.DUPLICATE;

    else if (youTrackLink.getType() == YouTrackLink.Type.SUBTASK && youTrackLink.getDirection().equals("INWARD"))
      type = AzureLink.Type.SUBTASK;

    else {
      logger_.log(Level.WARNING, "Unexpected link type: " + youTrackLink);
      type = AzureLink.Type.RELATED;
    }

    return new AzureLink(type, linkedWorkItem);
  }

  private static AzureLink newAzureLink(AzureWorkItem linkedWorkItem, JiraLink jiraLink)
  {
    AzureLink.Type type = null;

    switch (jiraLink.getType()) {
      case BLOCKS    : type = AzureLink.Type.RELATED;   break;
      case CLONES    : type = AzureLink.Type.DUPLICATE; break;
      case DUPLICATE : type = AzureLink.Type.DUPLICATE; break;
      case RELATES   : type = AzureLink.Type.RELATED;   break;
      case DEPENDS   : type = AzureLink.Type.SUBTASK;   break;
      case CAUSES    : type = AzureLink.Type.RELATED;   break;
      default :
        type = AzureLink.Type.RELATED;
    }

    return new AzureLink(type, linkedWorkItem);
  }

  private static AzureComment newAzureComment(YouTrackComment youTrackComment)
  {
    String text = youTrackComment.getText();
    YouTrackUser author = youTrackComment.getAuthor();
    Date createdTime = youTrackComment.getCreatedTime();

    return new AzureComment(text, newAzureUser(author), createdTime);
  }

  private static AzureComment newAzureComment(JiraComment jiraComment)
  {
    JiraUser author = jiraComment.getAuthor();
    Date createdTime = jiraComment.getCreatedTime();

    //
    // Comment text with reactions appended
    // We cannot properly add reactions on behalf of others, so leave it in text instead
    //
    StringBuilder s = new StringBuilder(jiraComment.getBody());
    Set<JiraReaction> jiraReactions = jiraComment.getReactions();
    if (!jiraReactions.isEmpty()) {
      s.append("<hr>Reactions</h3>");
      for (JiraReaction jiraReaction : jiraReactions) {
        s.append(jiraReaction.getFormattedString() + "<br>");
      }
    }

    String text = s.toString();

    return new AzureComment(text, newAzureUser(author), createdTime);
  }

  private static AzureAttachment newAzureAttachment(YouTrackAttachment youTrackAttachment)
  {
    return new AzureAttachment(youTrackAttachment.getName(),
                               youTrackAttachment.getContent());
  }

  private static AzureAttachment newAzureAttachment(JiraAttachment jiraAttachment)
  {
    return new AzureAttachment(jiraAttachment.getFileName(),
                               jiraAttachment.getContent());
  }

  /**
   * Return the equivalent Azure work item type for the specified YouTrack issue.
   *
   * @param youTrackIssue  You Track issue to get equivalent Azure work item type of. Non-null.
   * @return               Requested type. Never null.
   */
  private static String getAzureWorkItemType(YouTrackIssue youTrackIssue)
  {
    assert youTrackIssue != null : "youTrackIssue cannot be null";

    String customType = youTrackIssue.getCustomType();
    if (customType == null)
      return "Task";

    switch (customType) {
      case "Epic"    : return "Epic";
      case "Feature" : return "Feature";
      case "Task"    : return "User Story";
      case "Bug"     : return "Bug";
      case "SubTask" : return "Task";
      default :
        assert false : "Unrecognized type: " + customType;
        return null;
    }
  }

  /**
   * Return the equivalent Azure work item type for the specified Jira issue.
   *
   * @param jiraIssue  Jira issue to get equivalent Azure work item type of. Non-null.
   * @return           Requested type. Never null.
   */
  private static String getAzureWorkItemType(JiraIssue jiraIssue)
  {
    assert jiraIssue != null : "jiraIssue cannot be null";

    switch (jiraIssue.getType()) {
      case EPIC    : return "Epic";
      case STORY   : return "User Story";
      case BUG     : return "Bug";
      case TASK    : return "Task";
      case SUBTASK : return "Task";
      default :
        assert false : "Unrecognized type: " + jiraIssue.getType();
        return null;
    }
  }

  /**
   * Return the equivalent Azure work item state for the specified YouTrack issue.
   *
   * @param youTrackIssue  You Track issue to get equivalent Azure work item state of. Non-null.
   * @return               Requested state. Never null.
   */
  private static String getAzureWorkItemState(YouTrackIssue youTrackIssue)
  {
    assert youTrackIssue != null : "youTrackIssue cannot be null";

    String customKanbanStage = youTrackIssue.getCustomKanbanStage();
    if (customKanbanStage == null)
      return "To Do";

    switch (customKanbanStage) {
      case "Uncommitted"         : return "New";
      case "Development Backlog" : return "Backlog";
      case "To Do"               : return "To Do";
      case "In Progress"         : return "In Progress";
      case "Review"              : return "Review";
      case "Ready for test"      : return "Ready for test";
      case "Testing in progress" : return "Testing in Progress";
      case "Done"                : return "Done";
      case "Archived"            : return "Archived";
      case "Pulled back"         : return "To Do";
      case "To be released"      : return "Ready for Release";
      case "UAT"                 : return "UAT";
      case "Could not reproduce" : return "Done";
      case "7 Peaks"             : return "Archived";
      default :
        logger_.log(Level.WARNING, "Unexpected custom Kanban stage: " + customKanbanStage);
        return "New";
    }
  }

  /**
   * Return the equivalent Azure work item state for the specified Jira issue.
   *
   * @param jiraIssue  Jira issue to get equivalent Azure work item state of. Non-null.
   * @return           Requested state. Never null.
   */
  private static String getAzureWorkItemState(JiraIssue jiraIssue)
  {
    assert jiraIssue != null : "jiraIssue cannot be null";

    switch (jiraIssue.getStatus()) {
      case BACKLOG        : return "Backlog";
      case TODO           : return "To Do";
      case IN_PROGRESS    : return "In Progress";
      case CODE_REVIEW    : return "Review";
      case READY_FOR_TEST : return "Ready for test";
      case TEST           : return "Testing in Progress";
      case BLOCKED        : return "In Progress";
      case PO_REVIEW      : return "UAT";
      case DONE           : return "Done";
      default :
        logger_.log(Level.WARNING, "Unexpected Jira status: " + jiraIssue.getStatus());
        return "New";
    }
  }

  /**
   * Create a new Azure work item based on the specified Jira issue.
   *
   * @param jiraIssue  Jire issue to create equivalent Azure work item of. Non-null.
   * @return           The requested Azure work item. Never null.
   */
  private static AzureWorkItem newAzureWorkItem(JiraIssue jiraIssue)
  {
    assert jiraIssue != null : "jiraIssue cannot be null";

    long uniqueId = uniqueId_++;

    String areaPath = "<area>"; // Not path!

    String workItemType = getAzureWorkItemType(jiraIssue);

    String title = jiraIssue.getKey() + " " + jiraIssue.getSummary();

    // There is a length limit on work item titles of 255 characters, clip to that
    if (title.length() > 255)
      title = title.substring(0, 240) + "...";

    AzureUser createdBy = newAzureUser(jiraIssue.getCreator());

    Date createdTime = jiraIssue.getCreatedTime();

    //
    // Description
    //
    StringBuilder s = new StringBuilder(jiraIssue.getDescription());
    String customRootCause = jiraIssue.getCustomRootCause();
    String customRootCauseDescription = jiraIssue.getCustomRootCauseDescription();
    if (customRootCause != null) {
      s.append("<h3>Root cause</h3>");
      s.append(customRootCause);
      if (customRootCauseDescription != null) {
        s.append("<br>");
        s.append(customRootCauseDescription);
      }
    }
    String customExpectedAndActualResults = jiraIssue.getCustomExpectedAndActualResults();
    if (customExpectedAndActualResults != null && !customExpectedAndActualResults.trim().equals("")) {
      s.append("<h3>Expected and actual results</h3>");
      s.append(customExpectedAndActualResults);
    }

    String customAcceptanceCriteria = jiraIssue.getCustomAcceptanceCriteria();
    if (customAcceptanceCriteria != null && !customAcceptanceCriteria.trim().equals("")) {
      s.append("<h3>Acceptance criteria</h3>");
      s.append(customAcceptanceCriteria);
    }

    String customNotesResources = jiraIssue.getCustomNotesResources();
    if (customNotesResources != null && !customNotesResources.trim().equals("")) {
      s.append("<h3>Notes/resources</h3>");
      s.append(customNotesResources);
    }

    String customScreensFigma = jiraIssue.getCustomScreensFigma();
    if (customScreensFigma != null && !customScreensFigma.trim().equals("")) {
      s.append("<h3>Screens/Figma</h3>");
      s.append(customScreensFigma);
    }

    /*
    // Same for all. Not relevant really
    String customDefinitionOfDone = jiraIssue.getCustomDefinitionOfDone();
    if (customDefinitionOfDone != null && !customDefinitionOfDone.trim().equals("")) {
      s.append("<h3>Defininition of done</h3>");
      s.append(customDefinitionOfDone);
    }
    */

    String customHighLevelTestCases = jiraIssue.getCustomHighLevelTestCases();
    if (customHighLevelTestCases != null && !customHighLevelTestCases.trim().equals("")) {
      s.append("<h3>High-level test cases</h3>");
      s.append(customHighLevelTestCases);
    }

    String customScreensVideosResources = jiraIssue.getCustomScreensVideosResources();
    if (customScreensVideosResources != null && !customScreensVideosResources.trim().equals("")) {
      s.append("<h3>Screens/videos/resources</h3>");
      s.append(customScreensVideosResources);
    }

    String description = s.toString();

    JiraUser assignee = jiraIssue.getAssignee();
    AzureUser assignedTo = assignee != null ? newAzureUser(assignee) : null;

    JiraUser customQaAssignee = jiraIssue.getCustomQaAssignee();
    AzureUser tester = customQaAssignee != null ? newAzureUser(customQaAssignee) : null;

    AzureWorkItem parentWorkItem = null;

    String state = getAzureWorkItemState(jiraIssue);

    boolean isBlocked = jiraIssue.getStatus() == JiraIssue.Status.BLOCKED;

    // Project
    String project = "N/A";

    // Product
    String product = "DrillAware";

    // Priority
    int priority = 3;
    switch (jiraIssue.getPriority()) {
      case CRITICAL  : priority = 1; break;
      case MAJOR     : priority = 2; break;
      case NORMAL    : priority = 3; break;
      case MINOR     : priority = 4; break;
      case UNDEFINED : priority = 3; break;
      default :
        logger_.log(Level.WARNING, "Unexpected priority: " + jiraIssue.getPriority());
        priority = 3;
    }

    // Activity
    String activity = "Development";
    if (jiraIssue.getCustomPlatform() != null) {
      switch (jiraIssue.getCustomPlatform()) {
        case "Android"              : activity = "Development"; break;
        case "iOS"                  : activity = "Development"; break;
        case "KMP"                  : activity = "Development"; break;
        case "Backend"              : activity = "Development"; break;
        case "Web"                  : activity = "Development"; break;
        case "UI/UX"                : activity = "Design"; break;
        case "Platform Engineering" : activity = "DevOps"; break;
        case "QA"                   : activity = "Testing"; break;
        default :
          logger_.log(Level.WARNING, "Unexpected platform: " + jiraIssue.getCustomPlatform());
          activity = "";
      }
    }

    // Start date
    // Target date
    Date startDate = jiraIssue.getCustomStartDate();
    Date targetDate = jiraIssue.getDueDate();

    String systemInfo = jiraIssue.getCustomDevicesAndVersions();

    String foundIn = jiraIssue.getCustomEnv();

    String stepsToReproduce = jiraIssue.getStepsToReproduce();

    // Not supported by Jira
    String targetRelease = null;
    String plcVersion = null;
    String targetDtApp = null;

    AzureWorkItem azureWorkItem = new AzureWorkItem(uniqueId,
                                                    workItemType,
                                                    areaPath,
                                                    createdBy,
                                                    createdTime,
                                                    title,
                                                    description,
                                                    assignedTo,
                                                    tester,
                                                    parentWorkItem,
                                                    state,
                                                    isBlocked,
                                                    project,
                                                    product,
                                                    priority,
                                                    activity,
                                                    startDate,
                                                    targetDate,
                                                    systemInfo,
                                                    foundIn,
                                                    stepsToReproduce,
                                                    targetRelease,
                                                    plcVersion,
                                                    targetDtApp);

    // Tags
    for (String tag : jiraIssue.getLabels()) {
      azureWorkItem.addTag(tag);
    }

    // Comments
    for (JiraComment jiraComment : jiraIssue.getComments()) {
      azureWorkItem.addComment(newAzureComment(jiraComment));
    }

    logger_.log(Level.INFO, "In-memory Work Item created, temp ID: " + azureWorkItem.getUniqueId());

    return azureWorkItem;
  }

  /**
   * Create Azure Work Item from YouTRack issue.
   *
   * @param youTrackIssue  You Track issue to convert. Non-null.
   * @return               Associated Azure work item. Never null.
   */
  private static AzureWorkItem newAzureWorkItem(YouTrackIssue youTrackIssue)
  {
    assert youTrackIssue != null : "youTrackIssue cannot be null";

    long uniqueId = uniqueId_++;

    String workItemType = getAzureWorkItemType(youTrackIssue);

    String title = youTrackIssue.getIdReadable() + " " + youTrackIssue.getSummary();

    // There is a length limit on work item titles of 255 characters, clip to that
    if (title.length() > 255)
      title = title.substring(0, 240) + "...";

    AzureUser createdBy = newAzureUser(youTrackIssue.getReportedBy());

    Date createdTime = youTrackIssue.getCreatedTime();

    String description = youTrackIssue.getWikifiedDescription();

    YouTrackUser youTrackDeveloper = youTrackIssue.getDeveloper();
    AzureUser assignedTo = youTrackDeveloper != null ? newAzureUser(youTrackDeveloper) : null;

    YouTrackUser youTrackTester = youTrackIssue.getTester();
    AzureUser tester = youTrackTester != null ? newAzureUser(youTrackTester) : null;

    AzureWorkItem parentWorkItem = null;

    String state = getAzureWorkItemState(youTrackIssue);

    boolean isBlocked = youTrackIssue.getCustomKanbanState() != null &&
                        youTrackIssue.getCustomKanbanState().equals("Blocked");

    // Project
    // In YT there are only "R&D", "DrillScene", "DrillExpect", "DrillTronics", "DrillAware"
    // This is *product* in Azure so we set project to N/A
    String project = youTrackIssue.getProject();
    project = "N/A";

    // Product - Custom mapping here
    String product = "<product>";

    // AreaPath - Custom mapping here
    String areaPath = "<area>";

    // Priority - Custom mapping here
    int priority = 3;

    // Activity - Custom mapping here
    String activity = "<activityd>";

    Date startDate = null; // Not supported in YouTrack
    Date targetDate = null; // Not suported in YouTrack

    // Not supported in YouTrack
    String stepsToReproduce = null;

    AzureWorkItem azureWorkItem = new AzureWorkItem(uniqueId,
                                                    workItemType,
                                                    areaPath,
                                                    createdBy,
                                                    createdTime,
                                                    title,
                                                    description,
                                                    assignedTo,
                                                    tester,
                                                    parentWorkItem,
                                                    state,
                                                    isBlocked,
                                                    project,
                                                    product,
                                                    priority,
                                                    activity,
                                                    startDate,
                                                    targetDate,
                                                    systemInfo,
                                                    foundIn,
                                                    stepsToReproduce);

    // Tags
    for (String tag : youTrackIssue.getTags()) {
      azureWorkItem.addTag(tag);
    }

    // Comments
    for (YouTrackComment youTrackComment : youTrackIssue.getComments()) {
      azureWorkItem.addComment(newAzureComment(youTrackComment));
    }
    return azureWorkItem;
  }

  /**
   * Find the associated Azure work item for the given Jira issue.
   *
   * @param youTrackIssue  Jira issue to find Azure work item for. Non-null.
   * @return               The requested Azure work item, or null if not found.
   */
  private AzureWorkItem findExistingWorkItem(JiraIssue jiraIssue)
  {
    assert jiraIssue != null : "jiraIssue cannot be null";

    String key = jiraIssue.getKey();
    for (AzureWorkItem workItem : existingWorkItems_) {
      String title = workItem.getTitle();
      if (title.startsWith(key))
        return workItem;
    }

    // Not found
    return null;
  }

  /**
   * Find the associated Azure work item for the given YouTrack issue.
   *
   * @param youTrackIssue  You Track issue to find Azure work item for. Non-null.
   * @return               The requested Azure work item, or null if not found.
   */
  private AzureWorkItem findExistingWorkItem(YouTrackIssue youTrackIssue)
  {
    assert youTrackIssue != null : "youTrackIssue cannot be null";

    String idReadable = youTrackIssue.getIdReadable();
    for (AzureWorkItem workItem : existingWorkItems_) {
      String title = workItem.getTitle();
      if (title.startsWith(idReadable))
        return workItem;
    }

    // Not found
    return null;
  }

  /**
   * Find the associated Azure attachment for the given Jira attachment.
   *
   * @param jiraAttachmentId  ID of the Jira attachment to find Azure attachment of. Non-null.
   * @return                  The equivalent Azure attachment, or null if not found.
   */
  private AzureAttachment findAzureAttachment(String jiraAttachmentId)
  {
    assert jiraAttachmentId != null : "jiraAttchmentId cannot be null";

    for (Map.Entry<AzureAttachment,JiraAttachment> entry : jiraAttachments_.entrySet()) {
      AzureAttachment azureAttachment = entry.getKey();
      JiraAttachment jiraAttachment = entry.getValue();
      if (jiraAttachment.getId().equals(jiraAttachmentId))
        return azureAttachment;
    }

    // Not found
    return null;
  }

  /**
   * Update Jira or YouTRack URLs in the specified text so they point to the
   * equivalent Azure content.
   *
   * @param text  Text to update. Nopn-null.
   * @return      Updated text. Never null.
   */
  private String updateText(String text)
  {
    assert text != null : "text cannot be null";

    //
    // YouTrack: URL -> URL
    //
    for (Map.Entry<AzureAttachment,YouTrackAttachment> entry : youTrackAttachments_.entrySet()) {
      AzureAttachment azureAttachment = entry.getKey();
      YouTrackAttachment youTrackAttachment = entry.getValue();

      String originalUrl = youTrackAttachment.getUrl();
      originalUrl = originalUrl.replace("&", "&amp;");

      String newUrl = azureAttachment.getUrl();
      if (newUrl == null)
        continue;

      text = text.replace(originalUrl, newUrl);
    }

    //
    // Jira: <img src=".../<id>" ...> -> update src
    //
    Pattern pattern = Pattern.compile("<img\\s+[^>]*src=\"/rest/api/3/attachment/content/(\\d+)\"[^>]*>");
    Matcher matcher = pattern.matcher(text);

    StringBuffer s = new StringBuffer();
    while (matcher.find()) {
      String jiraAttachmentId = matcher.group(1); // e.g., "200262"
      AzureAttachment azureAttachment = findAzureAttachment(jiraAttachmentId);
      if (azureAttachment == null)
        continue;

      String url = azureAttachment.getUrl();
      if (url == null)
        continue;

      String newImgTag = "<img src=\"" + url + "\" />";

      matcher.appendReplacement(s, Matcher.quoteReplacement(newImgTag));
    }

    matcher.appendTail(s);

    text = s.toString();

    return text;
  }

  /**
   * Update the description of the specified work item,
   * i.e. update embedded attachment URLs.
   *
   * @param azureWorkItem  Work item to update description of. Non-null.
   */
  private void updateDescription(AzureWorkItem azureWorkItem)
  {
    assert azureWorkItem != null : "azureWorkItem cannot be null";

    String description = azureWorkItem.getDescription() != null ? azureWorkItem.getDescription() : "";
    description = updateText(description);
    azureWorkItem.setDescription(description);

    String stepsToReproduce = azureWorkItem.getStepsToReproduce() != null ? azureWorkItem.getStepsToReproduce() : "";
    stepsToReproduce = updateText(stepsToReproduce);
    azureWorkItem.setStepsToReproduce(stepsToReproduce);
  }

  /**
   * Update the comments of the specified work item,
   * i.e. update embedded attachment URLs within its comments.
   *
   * @param azureWorkItem  Work item to update description of. Non-null.
   */
  private void updateComments(AzureWorkItem azureWorkItem)
  {
    assert azureWorkItem != null : "azureWorkItem cannot be null";

    for (AzureComment comment : azureWorkItem.getComments()) {
      String text = comment.getText();
      text = updateText(text);
      comment.setText(text);
    }
  }

  /**
   * Process one YouTrack issue, i.e. migrate it to Azure.
   *
   * @param youTrackIssue  You Track issue to process. Non-null.
   */
  private void process(YouTrackIssue youTrackIssue)
  {
    assert youTrackIssue != null : "youTrackIssue cannot be null";

    logger_.log(Level.INFO, "Processing " + youTrackIssue.getIdReadable());

    // 1. Create an in-memory Azure work item
    AzureWorkItem azureWorkItem = newAzureWorkItem(youTrackIssue);
    azureToYouTrack_.put(azureWorkItem, youTrackIssue);
    youTrackToAzure_.put(youTrackIssue, azureWorkItem);

    // 2. Pull attachments, i.e. populate YouTrackAttachment.content_ accordingly
    youTrack_.pullAttachments(youTrackIssue);

    // 3. Create Azure attachment for each YouTrack attachment
    for (YouTrackAttachment youTrackAttachment : youTrackIssue.getAttachments()) {
      AzureAttachment azureAttachment = newAzureAttachment(youTrackAttachment);
      azureWorkItem.addAttachment(azureAttachment);

      youTrackAttachments_.put(azureAttachment, youTrackAttachment);
    }

    // 4. Upload attachments to Azure and update URL and name accordingly
    azure_.uploadAttachments(azureWorkItem);

    // 5. Update description URLs for attachments from YouTrack to Azure
    updateDescription(azureWorkItem);

    // 6. Push the work item to Azure. It will be in "New" state
    azure_.pushWorkItem(azureWorkItem);

    // 7. Set correct state
    azure_.pushState(azureWorkItem);

    // 8. Push all the attachments
    azure_.pushAttachments(azureWorkItem);

    // 9. Update comment URLs for attachments from YouTrack to Azure
    updateComments(azureWorkItem);

    // 10. Push all comments
    azure_.pushComments(azureWorkItem);

    System.out.println("------------------------------------------------------------------------");
    System.out.println("  " + youTrackIssue.getIdReadable() + " -> " + azureWorkItem.getId());
    System.out.println("------------------------------------------------------------------------");

    // 11. Reset attachments to save memory
    for (YouTrackAttachment youTrackAttachment : youTrackIssue.getAttachments()) {
      youTrackAttachment.setContent(null);
    }
    for (AzureAttachment azureAttachment : azureWorkItem.getAttachments()) {
      azureAttachment.setContent(null);
    }
  }

  /**
   * Process one Jira issue, i.e. migrate it to Azure.
   *
   * @param jiraIssue  Jira issue to process. Non-null.
   */
  private void process(JiraIssue jiraIssue)
  {
    assert jiraIssue != null : "jiraIssue cannot be null";

    System.out.println("Processing " + jiraIssue.getKey());

    // 1. Create an in-memory Azure work item
    AzureWorkItem azureWorkItem = newAzureWorkItem(jiraIssue);
    azureToJira_.put(azureWorkItem, jiraIssue);
    jiraToAzure_.put(jiraIssue, azureWorkItem);

    // 2. Pull attachments and populate JiraAttachment.content_ accordingly
    jira_.pullAttachments(jiraIssue);

    // 3. Create Azure attachment for each YouTrack attachment
    for (JiraAttachment jiraAttachment : jiraIssue.getAttachments()) {
      AzureAttachment azureAttachment = newAzureAttachment(jiraAttachment);
      azureWorkItem.addAttachment(azureAttachment);

      jiraAttachments_.put(azureAttachment, jiraAttachment);
    }

    // 4. Upload attachments to Azure and update URL and name accordingly
    azure_.uploadAttachments(azureWorkItem);

    // 5. Update description URLs for attachments from YouTrack to Azure
    updateDescription(azureWorkItem);

    // 6. Push the work item to Azure. It will be in "New" state
    azure_.pushWorkItem(azureWorkItem);

    // 7. Set correct state
    azure_.pushState(azureWorkItem);

    // 8. Push all the attachments
    azure_.pushAttachments(azureWorkItem);

    // 9. Update comment URLs for attachments from YouTrack to Azure
    updateComments(azureWorkItem);

    // 10. Push all comments
    azure_.pushComments(azureWorkItem);

    System.out.println("------------------------------------------------------------------------");
    System.out.println("  " + jiraIssue.getKey() + " -> " + azureWorkItem.getId() + "  complete");
    System.out.println("------------------------------------------------------------------------");
    System.out.println();

    // 11. Reset attachments to save memory
    for (JiraAttachment jiraAttachment : jiraIssue.getAttachments()) {
      jiraAttachment.setContent(null);
    }
    for (AzureAttachment azureAttachment : azureWorkItem.getAttachments()) {
      azureAttachment.setContent(null);
    }
  }

  /**
   * Process all YouTrack issues, i.e. load from back-end and migrate to Azure.
   */
  private void processYouTrackIssues()
  {
    // Pull all YouTrack issues
    List<YouTrackIssue> youTrackIssues = youTrack_.getIssues();

    // Process them one by one
    int nYouTrackIssues = youTrackIssues.size();
    for (int i = 0; i < nYouTrackIssues; i++) {
      YouTrackIssue youTrackIssue = youTrackIssues.get(i);
      String idReadable = youTrackIssue.getIdReadable();

      AzureWorkItem existingWorkItem = findExistingWorkItem(youTrackIssue);
      if (existingWorkItem != null) {
        System.out.println("==> " + i + ":" + nYouTrackIssues + " Work item already exists (" + idReadable + " = " + existingWorkItem.getId() + ")");
        azureToYouTrack_.put(existingWorkItem, youTrackIssue);
        youTrackToAzure_.put(youTrackIssue, existingWorkItem);
        continue;
      }

      System.out.println("==> " + (i+1) + ":" + nYouTrackIssues + " Processing " + idReadable);
      process(youTrackIssue);

      Util.reportMemory();
    }

    // Resolve links. Must happen after all AzureWorkItems has been created and pushed
    resolveYouTrackLinks();

    // Push links
    for (AzureWorkItem azureWorkItem : azureToYouTrack_.keySet()) {
      azure_.pushLinks(azureWorkItem);
    }
  }

  /**
   * Process all Jira issues, i.e. load from back-end and migrate to Azure.
   */
  private void processJiraIssues()
  {
    // Pull all YouTrack issues
    List<JiraIssue> jiraIssues = jira_.getIssues();

    // Process them one by one
    int nJiraIssues = jiraIssues.size();
    for (int i = 0; i < nJiraIssues; i++) {
      JiraIssue jiraIssue = jiraIssues.get(i);
      String key = jiraIssue.getKey();

      AzureWorkItem existingWorkItem = findExistingWorkItem(jiraIssue);
      if (existingWorkItem != null) {
        System.out.println("==> " + i + ":" + nJiraIssues + " Work item already exists (" + key + " = " + existingWorkItem.getId() + ")");
        azureToJira_.put(existingWorkItem, jiraIssue);
        jiraToAzure_.put(jiraIssue, existingWorkItem);
        continue;
      }

      System.out.println("==> " + (i+1) + ":" + nJiraIssues + " Processing " + key);
      process(jiraIssue);

      Util.reportMemory();
    }

    // Resolve links. Must happen after all AzureWorkItems has been created and pushed
    resolveJiraLinks();

    // Push links
    for (AzureWorkItem azureWorkItem : azureToJira_.keySet()) {
      azure_.pushLinks(azureWorkItem);
    }
  }

  /**
   * Load all Azure work items.
   * By this, a migration operation can be done incrementally.
   */
  private void loadExistingWorkItems()
  {
    existingWorkItems_.addAll(azure_.getWorkItems());
  }

  /**
   * Remove all Azure work items.
   */
  private void destroyAllWorkItems()
  {
    azure_.destroyAllWorkItems();
  }

  /**
   * Main program for migration from Jira/YouTRrack to Azure.
   *
   * @param arguments  Application arguments. Not used.
   */
  public static void main(String[] arguments)
  {
    MigrationTool migrationTool = new MigrationTool();
    //migrationTool.destroyAllWorkItems();
    //migrationTool.loadExistingWorkItems();

    //migrationTool.processYouTrackIssues();
    migrationTool.processYouTrackBusinessCaseIssues();
    //migrationTool.processJiraIssues();
  }
}
