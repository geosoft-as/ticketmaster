package no.geosoft.ticketmaster;

import javax.json.JsonObject;

/**
 * Model a user as defined in Azure.
 *
 * @author <a href="mailto:jacob.dreyer@geosoft.no">Jacob Dreyer</a>
 */
public final class AzureUser
{
  /** User login name. */
  private final String login_;

  /** User real name. */
  private final String fullName_;

  /** User email address. */
  private final String email_;

  public AzureUser(String login, String fullName, String email)
  {
    login_ = login;
    fullName_ = fullName;
    email_ = email;
  }

  public AzureUser(JsonObject jsonObject)
  {
    login_ = jsonObject.getString("uniqueName", null);
    fullName_ = jsonObject.getString("displayName", null);
    email_ = jsonObject.getString("uniqueName", null);
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
