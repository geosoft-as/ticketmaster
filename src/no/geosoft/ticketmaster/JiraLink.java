package no.geosoft.ticketmaster;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

public final class JiraLink
{
  public enum Type
  {
    BLOCKS("Blocks"),
    CLONES("Clones"),
    DUPLICATE("Duplicate"),
    RELATES("Relates"),
    DEPENDS("Depends"),
    CAUSES("Causes"),
    GANTT_END_TO_START("Gantt End to Start");

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

      return RELATES;
    }

    @Override
    public String toString()
    {
      return tag_;
    }
  }

  private final Type type_;

  private final String direction_;

  private final String linkedIssueId_;

  private JiraIssue linkedIssue_;

  public JiraLink(JsonObject jsonObject)
  {
    JsonObject typeObject = jsonObject.getJsonObject("type");
    type_ = Type.get(typeObject.getString("name", null));

    String direction = null;
    String linkedIssueId = null;

    JsonObject inwardIssue = jsonObject.getJsonObject("inwardIssue");
    if (inwardIssue != null) {
      linkedIssueId = inwardIssue.getString("id", null);
      direction = "INWARD";
    }

    JsonObject outwardIssue = jsonObject.getJsonObject("outwardIssue");
    if (outwardIssue != null) {
      linkedIssueId = outwardIssue.getString("id", null);
      direction = "OUTWARD";
    }

    direction_ = direction;
    linkedIssueId_ = linkedIssueId;
  }

  public Type getType()
  {
    return type_;
  }

  public String getDirection()
  {
    return direction_;
  }

  public String getLinkedIssueId()
  {
    return linkedIssueId_;
  }

  public JiraIssue getLinkedIssue()
  {
    return linkedIssue_;
  }

  public void setLinkedIssue(JiraIssue linkedIssue)
  {
    linkedIssue_ = linkedIssue;
  }

  @Override
  public String toString()
  {
    return type_ + " " + direction_ + " " + linkedIssueId_;
  }
}
