package no.geosoft.ticketmaster;

import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

public final class JiraComment implements Comparable<JiraComment>
{
  private final String id_;

  private final JiraUser author_;

  private final JiraUser updatedBy_;

  private final String body_;

  private final Date createdTime_;

  private final Date updatedTime_;

  private final Set<JiraReaction> reactions_ = new HashSet<>();

  public JiraComment(JsonObject jsonObject, JsonObject renderedJsonObject)
  {
    id_ = jsonObject.getString("id", null);

    author_ = new JiraUser(jsonObject.getJsonObject("author"));
    updatedBy_ = new JiraUser(jsonObject.getJsonObject("updateAuthor"));
    body_ = renderedJsonObject != null ? renderedJsonObject.getString("body", "") :
            jsonObject.getString("body", "");

    String createdTime = jsonObject.getString("created", null);
    createdTime_ = Util.getTime(createdTime);

    String updatedTime = jsonObject.getString("updated", null);
    updatedTime_ = Util.getTime(updatedTime);

    // To get reactions we need to do a separate comments query.
    // We currently don't, so this will always be null.
    JsonArray reactionsArray = renderedJsonObject.getJsonArray("reactions");

    if (reactionsArray != null) {
      for (JsonValue reactionValue : reactionsArray) {
        JsonObject reactionObject = reactionValue.asJsonObject();

        JiraReaction reaction = new JiraReaction(reactionObject);
        if (reaction.getCount() > 0)
          reactions_.add(reaction);
      }
    }
  }

  public String getId()
  {
    return id_;
  }

  public String getBody()
  {
    return body_;
  }

  public JiraUser getAuthor()
  {
    return author_;
  }

  public Date getCreatedTime()
  {
    return new Date(createdTime_.getTime());
  }

  public JiraUser getUpdatedBy()
  {
    return updatedBy_;
  }

  public Date getUpdatedTime()
  {
    return new Date(updatedTime_.getTime());
  }

  public Set<JiraReaction> getReactions()
  {
    return Collections.unmodifiableSet(reactions_);
  }

  @Override
  public int compareTo(JiraComment jiraComment)
  {
    Date time1 = createdTime_;
    Date time2 = jiraComment.createdTime_;

    return time1 != null && time2 != null ? time1.compareTo(time2) : 1;
  }

  @Override
  public String toString()
  {
    return author_ + " [" + createdTime_ + "] " + body_;
  }
}
