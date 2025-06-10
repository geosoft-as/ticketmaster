package no.geosoft.ticketmaster;

import java.util.Date;

import javax.json.JsonObject;
import javax.json.JsonNumber;

/**
 * Model a comment as defined in YouTrack.
 *
 * @author <a href="mailto:jacob.dreyer@geosoft.no">Jacob Dreyer</a>
 */
public final class YouTrackComment implements Comparable<YouTrackComment>
{
  private final String id_;

  private final String text_;

  private final Date createdTime_;

  private final boolean isDeleted_;

  private final YouTrackUser author_;

  public YouTrackComment(JsonObject jsonObject)
  {
    id_ = jsonObject.getString("id", null);

    text_ = jsonObject.getString("textPreview", null);

    JsonObject author = jsonObject.getJsonObject("author");
    author_ = author != null ? new YouTrackUser(author) : null;

    isDeleted_ = jsonObject.getBoolean("deleted", false);

    JsonNumber createdTime = jsonObject.getJsonNumber("created");
    createdTime_ = createdTime != null ? new Date(createdTime.longValue()) : null;
  }

  public String getId()
  {
    return id_;
  }

  public String getText()
  {
    return text_;
  }

  public Date getCreatedTime()
  {
    return createdTime_ != null ? new Date(createdTime_.getTime()) : null;
  }

  public boolean isDeleted()
  {
    return isDeleted_;
  }

  public YouTrackUser getAuthor()
  {
    return author_;
  }

  @Override
  public int compareTo(YouTrackComment youTrackComment)
  {
    Date time1 = createdTime_;
    Date time2 = youTrackComment.createdTime_;

    return time1 != null && time2 != null ? time1.compareTo(time2) : 1;
  }

  @Override
  public String toString()
  {
    return "Comment by " + author_ + " @ " + createdTime_;
  }
}
