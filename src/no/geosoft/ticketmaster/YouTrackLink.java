package no.geosoft.ticketmaster;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

public final class YouTrackLink
{
  public enum Type
  {
    RELATES("Relates"),
    DEPEND("Depend"),
    DUPLICATE("Duplicate"),
    SUBTASK("Subtask");

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

  private final String id_;

  private final String direction_;

  private final Type type_;

  private final String linkedIssueId_;

  private YouTrackIssue linkedIssue_;

  public YouTrackLink(JsonObject jsonObject)
  {
    id_ = jsonObject.getString("id", null);
    direction_ = jsonObject.getString("direction", null);

    JsonObject linkTypeObject = jsonObject.getJsonObject("linkType");
    String linkType = linkTypeObject != null ? linkTypeObject.getString("name", null) : null; // TODO: Check why!
    type_ = linkType != null ? Type.get(linkType) : null;

    String linkedIssueId = null;
    JsonArray linkedIssues = jsonObject.getJsonArray("issues");
    for (JsonValue linkedIssue : linkedIssues) {
      linkedIssueId = ((JsonObject) linkedIssue).getString("id");
      break;
    }
    linkedIssueId_ = linkedIssueId;
  }

  public String getId()
  {
    return id_;
  }

  public String getDirection()
  {
    return direction_;
  }

  public Type getType()
  {
    return type_;
  }

  public String getLinkedIssueId()
  {
    return linkedIssueId_;
  }

  public YouTrackIssue getLinkedIssue()
  {
    return linkedIssue_;
  }

  public void setLinkedIssue(YouTrackIssue linkedIssue)
  {
    linkedIssue_ = linkedIssue;
  }

  @Override
  public String toString()
  {
    return type_ + " " + direction_ + " " + linkedIssueId_;
  }
}
