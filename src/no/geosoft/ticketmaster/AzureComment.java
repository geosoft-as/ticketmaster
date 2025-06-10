package no.geosoft.ticketmaster;

import java.util.Date;

public final class AzureComment implements Comparable<AzureComment>
{
  private final AzureUser author_;

  private final Date createdTime_;

  private String text_;

  public AzureComment(String text,
                      AzureUser author,
                      Date createdTime)
  {
    text_ = text;
    author_ = author;
    createdTime_ = new Date(createdTime.getTime());
  }

  public String getText()
  {
    return text_;
  }

  public void setText(String text)
  {
    text_ = text;
  }

  public String getExtendedText()
  {
    StringBuilder s = new StringBuilder();
    s.append("<i>");
    s.append("Commented by ");
    s.append("<a href='mailto:" + author_.getEmail() + "'>" + author_.getFullName() + "</a>");
    s.append(" on " + Util.DATE_FORMAT.format(createdTime_) + ":");
    s.append("</i>");
    s.append("<br>");
    if (text_ != null)
      s.append(text_);

    return s.toString();
  }

  public AzureUser getAuthor()
  {
    return author_;
  }

  public Date getCreatedTime()
  {
    return new Date(createdTime_.getTime());
  }

  @Override
  public int compareTo(AzureComment azureComment)
  {
    Date time1 = createdTime_;
    Date time2 = azureComment.createdTime_;

    return time1 != null && time2 != null ? time1.compareTo(time2) : 1;
  }

  @Override
  public String toString()
  {
    return text_;
  }
}
