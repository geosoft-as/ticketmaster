package no.geosoft.ticketmaster;

import java.util.HashMap;
import java.util.Map;

import javax.json.JsonObject;

public final class JiraUser
{
  private final String id_;

  private final String login_;

  private final String email_;

  private final String name_;

  /**
   * Since we are using a non-adm user for retrieveing Jira information we don't get
   * email addresses through the REST API. We have collected these frm elsewhere.
   */
  private final static Map<String,String> userEmails_ = new HashMap<>();

  static {
    userEmails_.put("<name>", "<email");
  }

  public JiraUser(JsonObject jsonObject)
  {
    id_ = jsonObject.getString("accountId", null);
    name_ = jsonObject.getString("displayName", null);
    email_ = email;
    login_ = email;
  }

  public String getId()
  {
    return id_;
  }

  public String getLogin()
  {
    return login_;
  }

  public String getEmail()
  {
    return email_;
  }

  public String getName()
  {
    return name_;
  }

  @Override
  public String toString()
  {
    return email_ + " (" + name_ + ")";
  }

  @Override
  public boolean equals(Object object)
  {
    if (object == null)
      return false;

    if (!(object instanceof JiraUser))
      return false;

    JiraUser user = (JiraUser) object;
    return id_.equals(user.id_);
  }

  @Override
  public int hashCode()
  {
    return id_.hashCode();
  }
}
