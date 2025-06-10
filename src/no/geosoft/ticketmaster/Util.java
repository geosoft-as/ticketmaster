package no.geosoft.ticketmaster;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.json.JsonWriter;
import javax.json.JsonWriterFactory;
import javax.json.stream.JsonGenerator;

public final class Util
{
  /** The logger instance. */
  private static final Logger logger_ = Logger.getLogger(Util.class.getName());

  /** Common date format. */
  static final DateFormat DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy HH:mm");

  private Util()
  {
    assert false : "This constructor should never be called";
  }

  public static void reportMemory()
  {
    Runtime runtime = Runtime.getRuntime();
    runtime.gc();

    long totalMemory = runtime.totalMemory();
    long freeMemory = runtime.freeMemory();
    long usedMemory = totalMemory - freeMemory;

    System.out.printf("Used Memory: %.2f MB | Free Memory: %.2f MB | Total Memory: %.2f MB%n",
                      usedMemory / (1024.0 * 1024.0),
                      freeMemory / (1024.0 * 1024.0),
                      totalMemory / (1024.0 * 1024.0));
  }

  public static String toPretty(JsonValue jsonValue)
  {
    Map<String, Object> config = new HashMap<>();
    config.put(JsonGenerator.PRETTY_PRINTING, true);

    JsonWriterFactory writerFactory = Json.createWriterFactory(config);
    StringWriter stringWriter = new StringWriter();
    try (JsonWriter jsonWriter = writerFactory.createWriter(stringWriter)) {
        jsonWriter.write(jsonValue);
      }

    return stringWriter.toString();
  }

  public static JsonObject getJsonObject(JsonObject parentObject, String name)
  {
    JsonValue value = parentObject.get(name);
    return value != null && value.getValueType() == JsonValue.ValueType.OBJECT ? (JsonObject) value : null;
  }

  public static Date getTime(String dateString)
  {
    if (dateString == null)
      return null;

    try {
      return ISO8601DateParser.parse(dateString);
    }
    catch (Exception exception) {
      assert false : "Programming error";
      return null;
    }
  }

  public static String toString(Date time)
  {
    return time != null ? ISO8601DateParser.toString(time) : null;
  }

  public static void close(Closeable stream)
  {
    try {
      if (stream != null)
        stream.close();
    }
    catch (IOException exception) {
      logger_.log(Level.WARNING, "Unable tyo close stream: " + stream);
    }
  }

  public static void close(HttpURLConnection connection)
  {
    if (connection != null)
      connection.disconnect();
  }

  public static URL newUrl(String urlString)
  {
    try {
      return new URL(urlString);
    }
    catch (MalformedURLException exception) {
      assert false : "Programming error: " + urlString;
      return null;
    }
  }

  public static String normalizeUrl(String url)
  {
    return url.toLowerCase().replaceAll("/+$", "");
  }

  public static String urlEncode(String text)
  {
    return text.replace(" ", "%20");
  }

  public static String getError(InputStream stream)
  {
    try {
      BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
      String inputLine;
      StringBuilder response = new StringBuilder();
      while ((inputLine = reader.readLine()) != null) {
        response.append(inputLine);
      }
      reader.close();

      return response.toString();
    }
    catch (IOException exception) {
      logger_.log(Level.WARNING, "Unable to get error");
      return null;
    }
  }
}
