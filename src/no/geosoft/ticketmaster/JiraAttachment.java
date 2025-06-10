package no.geosoft.ticketmaster;

import java.util.Date;

import javax.json.JsonObject;

public final class JiraAttachment
{
  private final String id_;

  private final String fileName_;

  private final JiraUser author_;

  private final Date createdTime_;

  private final String mimeType_;

  private final String url_;

  private byte[] content_;

  public JiraAttachment(JsonObject jsonObject)
  {
    id_ = jsonObject.getString("id", null);
    fileName_ = jsonObject.getString("filename", null);
    mimeType_ = jsonObject.getString("mimeType", null);
    author_ = new JiraUser(jsonObject.getJsonObject("author"));

    String createdTime = jsonObject.getString("created", null);
    createdTime_ = Util.getTime(createdTime);

    url_ = jsonObject.getString("content", null);
  }

  public String getId()
  {
    return id_;
  }

  public String getFileName()
  {
    return fileName_;
  }

  public JiraUser getAuthor()
  {
    return author_;
  }

  public Date getCreatedTime()
  {
    return new Date(createdTime_.getTime());
  }

  public String getMimeType()
  {
    return mimeType_;
  }

  public String getUrl()
  {
    return url_;
  }

  public void setContent(byte[] content)
  {
    content_ = content;
  }

  public byte[] getContent()
  {
    return content_;
  }

  @Override
  public String toString()
  {
    return fileName_;
  }
}
