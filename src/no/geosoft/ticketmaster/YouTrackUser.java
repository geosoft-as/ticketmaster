package no.geosoft.ticketmaster;

import javax.json.JsonObject;

/**
 * Model a user as defined in YouTrack.
 *
 * @author <a href="mailto:jacob.dreyer@geosoft.no">Jacob Dreyer</a>
 */
public final class YouTrackUser
{
  /** YouTrack ID. */
  private final String id_;

  /** User login name. */
  private final String login_;

  /** User real name. */
  private final String fullName_;

  /** User email address. */
  private final String email_;

  public YouTrackUser(JsonObject jsonObject)
  {
    id_ = jsonObject.getString("id", null);
    login_ = jsonObject.getString("login", null);

    String fullName = jsonObject.getString("fullName", null);
    if (fullName == null)
      fullName = jsonObject.getString("name", null);

    fullName_ = fullName;
    email_ = jsonObject.getString("email", null);
  }

  public String getId()
  {
    return id_;
  }

  public String getLogin()
  {
    return login_;
  }

  public String getFullName()
  {
    return fullName_;
  }

  public String getEmail()
  {
    return email_;
  }

  @Override
    public String toString()
  {
    return fullName_ + " (" + email_ + ")";
  }
}
