package no.geosoft.ticketmaster;

import javax.json.JsonObject;

/**
 * Class modelling a YouTrack attachment.
 *
 * @author <a href="mailto:jacob.dreyer@geosoft.no">Jacob Dreyer</a>
 */
public final class YouTrackAttachment
{
  /** Attachment name. */
  private final String name_;

  /** ULR to the attachment. */
  private final String url_;

  private byte[] content_;

  public YouTrackAttachment(JsonObject jsonObject)
  {
    name_ = jsonObject.getString("name", null);
    url_ = jsonObject.getString("url", null);
  }

  public String getName()
  {
    return name_;
  }

  public String getExtension()
  {
    int p = name_.lastIndexOf(".");
    return p != -1 ? name_.substring(p + 1) : "";
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
    return name_ + " URL=" + url_;
  }
}

