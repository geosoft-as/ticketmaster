package no.geosoft.ticketmaster;

public final class AzureLink
{
  public enum Type
  {
    RELATED("Related"),
    DUPLICATE("Duplicate"),
    DEPENDS_ON("DependsOn"),
    SUBTASK("Subtask");

    private final String tag_;

    private Type(String tag)
    {
      tag_ = tag;
    }

    private static Type get(String tag)
    {
      for (Type type : Type.values())
        if (type.tag_.equals(tag))
          return type;

      assert false : "Missing type: " + tag;
      return null;
    }

    @Override
    public String toString()
    {
      return tag_;
    }
  }

  private final Type type_;

  private final AzureWorkItem linkedWorkItem_;

  public AzureLink(Type type, AzureWorkItem linkedWorkItem)
  {
    if (type == null)
      throw new IllegalArgumentException("type cannot be null");

    if (linkedWorkItem == null)
      throw new IllegalArgumentException("linkedWorkItem cannot be null");

    type_ = type;
    linkedWorkItem_ = linkedWorkItem;
  }

  public Type getType()
  {
    return type_;
  }

  public AzureWorkItem getLinkedWorkItem()
  {
    return linkedWorkItem_;
  }

  @Override
  public String toString()
  {
    return type_ + " " + linkedWorkItem_.getId();
  }
}

