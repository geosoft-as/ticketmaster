package no.geosoft.ticketmaster;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

public final class JiraReaction
{
  private final String emoji_;

  private final Set<JiraUser> users_ = new HashSet<>();

  public JiraReaction(String emoji)
  {
    emoji_ = emoji;
  }

  public JiraReaction(JsonObject jsonObject)
  {
    emoji_ = jsonObject.getString("emoji", null);

    JsonArray users = jsonObject.getJsonArray("reactionGroup");
    for (JsonValue user : users) {
      JsonObject userObject = user.asJsonObject();
      users_.add(new JiraUser(userObject));
    }
  }

  public String getEmoji()
  {
    return emoji_;
  }

  public int getCount()
  {
    return users_.size();
  }

  public void addUser(JiraUser user)
  {
    users_.add(user);
  }

  public Set<JiraUser> getUsers()
  {
    return Collections.unmodifiableSet(users_);
  }

  public String getFormattedString()
  {
    StringBuilder s = new StringBuilder();
    s.append(emoji_);
    s.append(" by ");
    boolean isFirst = true;
    for (JiraUser user : users_) {
      s.append(isFirst ? "" : ", ");
      s.append(user.getName());
      isFirst = false;
    }
    return s.toString();
  }

  @Override
  public String toString()
  {
    return emoji_;
  }
}
