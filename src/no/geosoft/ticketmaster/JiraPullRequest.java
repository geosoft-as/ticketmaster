package no.geosoft.ticketmaster;

import javax.json.JsonObject;

public final class JiraPullRequest
{
  private final String id_;

  private final String url_;

  private final String title_;

  private final String state_;

  public JiraPullRequest(JsonObject jsonObject)
  {
    id_ = jsonObject.getString("id", null);
    url_ = jsonObject.getString("url", null);
    title_ = jsonObject.getString("title", null);
    state_ = jsonObject.getString("state", null);
  }

  public String getId()
  {
    return id_;
  }

  public String getUrl()
  {
    return url_;
  }

  public String getTitle()
  {
    return title_;
  }

  public String getState()
  {
    return state_;
  }

  @Override
  public String toString()
  {
    return title_;
  }
}
