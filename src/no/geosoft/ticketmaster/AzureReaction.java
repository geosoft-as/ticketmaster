package no.geosoft.ticketmaster;

import java.util.HashSet;
import java.util.Set;

import javax.json.JsonArray;
import javax.json.JsonObject;

public final class AzureReaction
{
  private final String emoji_;

  private final Set<AzureUser> users_ = new HashSet<>();

  public AzureReaction(String emoji)
  {
    emoji_ = emoji;
  }

  public String getEmoji()
  {
    return emoji_;
  }

  public int getCount()
  {
    return users_.size();
  }

  public void addUser(AzureUser user)
  {
    users_.add(user);
  }

  public Set<User> getUsers()
  {
    return Collections.unmodifiableSet(users_);
  }

  @Override
  public String toString()
  {
    return emoji_;
  }
}
