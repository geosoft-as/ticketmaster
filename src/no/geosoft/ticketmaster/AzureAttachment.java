package no.geosoft.ticketmaster;

/**
 * Modelling a work item attachment.
 *
 * @author <a href="mailto:jacob.dreyer@geosoft.no">Jacob Dreyer</a>
 */
public final class AzureAttachment
{
  private byte[] content_;

  private String fileName_;

  private String url_;

  // Content might be null if something failed. We still want the attachment instance.
  public AzureAttachment(String fileName, byte[] content)
  {
    if (fileName == null)
      throw new IllegalArgumentException("fileName cannot be null");

    fileName_ = fileName;
    content_ = content;
  }

  public byte[] getContent()
  {
    return content_;
  }

  public void setContent(byte[] content)
  {
    content_ = content;
  }

  public String getFileName()
  {
    return fileName_;
  }

  public void setFileName(String fileName)
  {
    fileName_ = fileName;
  }

  public String getExtension()
  {
    int p = fileName_.lastIndexOf(".");
    return p != -1 ? fileName_.substring(p + 1) : "";
  }

  public void setUrl(String url)
  {
    url_ = url;
  }

  public String getUrl()
  {
    return url_;
  }

  @Override
  public String toString()
  {
    return fileName_;
  }
}

