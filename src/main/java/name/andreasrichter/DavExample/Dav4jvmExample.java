package name.andreasrichter.DavExample;

import java.io.IOException;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import at.bitfire.dav4jvm.BasicDigestAuthHandler;
import at.bitfire.dav4jvm.DavCalendar;
import at.bitfire.dav4jvm.DavResource;
import at.bitfire.dav4jvm.Property;
import at.bitfire.dav4jvm.exception.DavException;
import at.bitfire.dav4jvm.exception.HttpException;
import at.bitfire.dav4jvm.property.CalendarHomeSet;
import at.bitfire.dav4jvm.property.CurrentUserPrincipal;
import at.bitfire.dav4jvm.property.ResourceType;
import at.bitfire.dav4jvm.property.SupportedCalendarComponentSet;
import at.bitfire.dav4jvm.Response.HrefRelation;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Dav4jvmExample {

    // replace this with your WebDAV server URL, or set environment variable CALDAV_URL
    private static final String serverURL = System.getenv("CALDAV_URL");
    // username of user accessing the WebDAV server, taken from environment variable CALDAV_USERNAME
    private static final String user = System.getenv("CALDAV_USERNAME");
    // password for user, taken from environment variable CALDAV_PASSWORD
    private static final String password = System.getenv("CALDAV_PASSWORD");
    
    private static final String icsPattern =
            "BEGIN:VCALENDAR\r\n"+
            "CALSCALE:GREGORIAN\r\n"+
            "VERSION:2.0\r\n"+
            "PRODID:testapp\r\n"+
            "BEGIN:VTIMEZONE\r\n"+
            "TZID:Europe/Berlin\r\n"+
            "BEGIN:DAYLIGHT\r\n"+
            "TZOFFSETFROM:+0100\r\n"+
            "TZOFFSETTO:+0200\r\n"+
            "TZNAME:CEST\r\n"+
            "DTSTART:19700329T020000\r\n"+
            "RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU\r\n"+
            "END:DAYLIGHT\r\n"+
            "BEGIN:STANDARD\r\n"+
            "TZOFFSETFROM:+0200\r\n"+
            "TZOFFSETTO:+0100\r\n"+
            "TZNAME:CET\r\n"+
            "DTSTART:19701025T030000\r\n"+
            "RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU\r\n"+
            "END:STANDARD\r\n"+
            "END:VTIMEZONE\r\n"+
            "BEGIN:VEVENT\r\n"+
            "CREATED:%s\r\n"+ //20250829T091321Z
            "DTSTAMP:%s\r\n"+ //20250829T091332Z
            "LAST-MODIFIED:%s\r\n"+ //20250829T091332Z
            "SEQUENCE:2\r\n"+
            "UID:%s\r\n"+
            "DTSTART;TZID=Europe/Berlin:%s\r\n"+ //20250829T130000
            "DTEND;TZID=Europe/Berlin:%s\r\n"+ //20250829T133000
            "STATUS:CONFIRMED\r\n"+
            "SUMMARY:test appointment\r\n"+
            "END:VEVENT\r\n" +
            "END:VCALENDAR\r\n";
    

    private static final Property.Name calendarType = ResourceType.Companion.getCALENDAR();
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final String TZID="Europe/Berlin";
    
    private HttpUrl calendarUrl;

    /**
     * Retrieves the current-user-principal URL for the authenticated user.
     * <p>
     * This method sends a PROPFIND request to the given URL to discover the
     * {@code <DAV:current-user-principal>} property. This property identifies
     * the principal resource that corresponds to the authenticated user.
     * <p>
     * It uses an {@link OkHttpClient} configured with Basic/Digest authentication
     * and does not follow redirects automatically.
     *
     * @param url The URL to query for the current-user-principal property.
     *            This is typically the initial service discovery URL (e.g., from .well-known).
     * @return The {@link HttpUrl} of the current-user-principal if found and resolved correctly,
     *         or {@code null} if the property is not found, cannot be resolved,
     *         or if an error occurs during the PROPFIND request.
     */
    private static HttpUrl getCurrentUserPrincipal( HttpUrl url) {
        // Set up the OkHttp client with authentication
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .authenticator(new BasicDigestAuthHandler(null, user, password, false))
                .followRedirects(false)
                .build();

        final HttpUrl[] principal = {null};
        // propfind
        try {
            new DavResource(httpClient, url).propfind(0, new Property.Name[]{CurrentUserPrincipal.NAME}, (response, throwable) -> {
                if (response != null) {
                    CurrentUserPrincipal currentUserPrincipal = response.get(CurrentUserPrincipal.class);
                    if (currentUserPrincipal != null && currentUserPrincipal.getHref() != null) {
                        HttpUrl resolvedUrl = response.getRequestedUrl().resolve(currentUserPrincipal.getHref());
                        if (resolvedUrl != null) {
                            System.out.println("Found current-user-principal: " + resolvedUrl);
                            principal[0] = resolvedUrl;
                        }
                    }
                } else if (throwable != null) {
                    System.out.println("PROPFIND request failed: " + throwable.toString());
                }
            });
        } catch (IOException|DavException e) {
            System.out.println("error occured: "+e.getMessage());
        }
        return principal[0];
    }

    /**
     * Retrieves the calendar-home-set URL for the authenticated user.
     * <p>
     * This method sends a PROPFIND request to the given URL to discover the
     * {@code <DAV:calendar-home-set>} property. This property typically points
     * to the collection that contains the user's calendars.
     * <p>
     * It uses an {@link OkHttpClient} configured with Basic/Digest authentication
     * and does not follow redirects automatically.
     *
     * @param url The URL to query for the calendar-home-set property. This is typically
     *            the current-user-principal URL.
     * @return The {@link HttpUrl} of the calendar-home-set if found and resolved correctly,
     *         or {@code null} if the property is not found, cannot be resolved,
     *         or if an error occurs during the PROPFIND request.
     */
    private static HttpUrl getCalendarHomeSet( HttpUrl url) {
    // 1. Set up the OkHttp client with authentication
    OkHttpClient httpClient = new OkHttpClient.Builder()
            .authenticator(new BasicDigestAuthHandler(null, user, password, false))
            .followRedirects(false)
            .build();

    final HttpUrl[] homeSet = {null};
    // propfind
    try {
        new DavResource(httpClient, url).propfind(0, new Property.Name[]{CalendarHomeSet.NAME}, (response, throwable) -> {
            if (response != null) {
                CalendarHomeSet calendarHomeSet = response.get(CalendarHomeSet.class);
                if (calendarHomeSet != null && calendarHomeSet.getHref() != null) {
                    HttpUrl resolvedUrl = response.getRequestedUrl().resolve(calendarHomeSet.getHref());
                    if (resolvedUrl != null) {
                        System.out.println("Found calendar-home-set: " + resolvedUrl);
                        homeSet[0] = resolvedUrl;
                    }
                }
            } else if (throwable != null) {
                System.out.println("PROPFIND request failed: " + throwable.toString());
            }
        });
    } catch (IOException|DavException e) {
        System.out.println("error occured: "+e.getMessage());
    }
    return homeSet[0];
}


    /**
     * Extracts the CalDAV path from a server using the .well-known URI.
     * <p>
     * This method sends a HEAD request to {@code server/.well-known/caldav}
     * and expects a redirect (3xx status code) with a {@code Location} header
     * pointing to the actual CalDAV service path.
     * <p>
     * It handles:
     * <ul>
     *     <li>Adding {@code https://} prefix if the server URL doesn't have a scheme.</li>
     *     <li>Removing trailing slashes from the server URL.</li>
     *     <li>Ensuring HTTP/1.1 is used to avoid issues with servers not supporting HTTP/2.</li>
     *     <li>Not following redirects automatically, as the {@code Location} header is needed.</li>
     * </ul>
     *
     * @param server The base URL of the CalDAV server (e.g., "example.com" or "https://caldav.example.com").
     * @return The CalDAV path extracted from the {@code Location} header of the redirect response,
     *         or {@code null} if the path cannot be determined (e.g., no redirect, no Location header, or network error).
     * @throws IOException If an I/O error occurs during the HTTP request.
     */
    private static String extractCaldavPath(String server) throws IOException {
        // Build the base URL, handling potential trailing slashes and http(s) prefixes.
        String baseUrl = server;
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            baseUrl = "https://" + baseUrl;
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        String url = baseUrl + "/.well-known/caldav";

        OkHttpClient client = new OkHttpClient.Builder()
                // Setting a list of protocols to ensure HTTP/1.1 is supported.
                // Some servers may not support HTTP/2 and this can lead to connection issues.
                .protocols(Collections.singletonList(Protocol.HTTP_1_1))
                .followRedirects(false) // We want to read the Location header ourselves.
                .build();

        Request request = new Request.Builder()
                .url(url)
                .head() // Use HEAD request as we only need headers, not the body.
                .build();

        System.out.println("looking for file "+ url.toString());
        try (Response response = client.newCall(request).execute()) {
            System.out.println(response.toString());
            if (response.isSuccessful() || response.isRedirect()) {                
                // The Location header is case-insensitive, so we use header() which is also case-insensitive.
                String locationHeader = response.header("Location");
                if (locationHeader != null && !locationHeader.isEmpty()) {
                    return locationHeader;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Finds and returns the first ResourceType object in a list of properties.
     *
     * @param properties The list of properties to search.
     * @return The found ResourceType object, or null if not found.
     */
    private static ResourceType findResourceType(List<Property> properties) {
        for (Property property : properties) {
            if (property instanceof ResourceType) {
                return (ResourceType) property;
            }
        }
        return null;
    }
    
    /**
     * Creates a sample ICS (iCalendar) string for testing purposes.
     *
     * <p>This method generates an ICS string representing a single event.
     * The event is scheduled for the next day at 13:00 (Europe/Berlin timezone)
     * and lasts for one hour.
     *
     * <p>The timestamps within the ICS string (CREATED, DTSTAMP, LAST-MODIFIED)
     * are set to the current time or a second after. The DTSTART and DTEND
     * are formatted according to the {@code DATE_TIME_FORMATTER}.
     *
     * <p>The method uses a predefined {@code icsPattern} which includes a VTIMEZONE
     * definition for Europe/Berlin and a VEVENT with a summary.
     * The UID for the event is randomly generated.
     *
     * @return A {@link String} containing the formatted ICS data for a sample event.
     *         The string is generated by formatting the {@code icsPattern} with
     *         calculated timestamps and a random UID.
     */
    private static String createICSString() {
        String icsString=null;
        // create timestamps
        // current time/date
        ZonedDateTime now = ZonedDateTime.now();
        String nowString=now.format(DATE_TIME_FORMATTER);
        ZonedDateTime nowPlus = now.plusSeconds(1);
        String nowPlusString = nowPlus.format(DATE_TIME_FORMATTER);
        // Add one day to the current timestamp get to the next day
        // and set time to 13:00.
        ZonedDateTime start = now.plusHours(24)
                .withHour(13)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
        String startString = start.format(DATE_TIME_FORMATTER);
        ZonedDateTime end = start.plusHours(1);
        String endString = end.format(DATE_TIME_FORMATTER);
        UUID uuid = UUID.randomUUID();
        icsString=String.format(icsPattern,nowString,nowPlusString,nowString,uuid,startString,endString);
        return icsString;
    }
    
    public static void uploadICSString(HttpUrl calendarUrl, String eventString) {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .authenticator(new BasicDigestAuthHandler(null, user, password, false))
                .followRedirects(false)
                .build();
        UUID uuid = UUID.randomUUID();
        HttpUrl.Builder urlBuilder = calendarUrl.newBuilder();
        HttpUrl icsFileUrl = urlBuilder.addPathSegment(uuid + ".ics").build();
        DavCalendar uploadCalender = new DavCalendar(httpClient, icsFileUrl);
        RequestBody requestBody = RequestBody.create(eventString, DavCalendar.Companion.getMIME_ICALENDAR_UTF8());
    
        try {
            uploadCalender.put(requestBody, null, null, false, (response) -> {
                System.out.println("response when trying to upload ICS File: " + response.toString());
                if (response.code()==201) {
                    System.out.println("calendar entry successfully created.");
                }
            });
        } catch (IOException | HttpException e) {
            System.out.println("error when uploading ICS file: "+ e.getMessage());
        }
        
        
    }


    public static void main(String[] args) {
        // 1. Set up the OkHttp client with authentication
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .authenticator(new BasicDigestAuthHandler(null, user, password, false))
                .followRedirects(false)
                .build();
        // 2. get wellknown-path
        String wellknown_path;
        try {
            wellknown_path = extractCaldavPath(serverURL);
            System.out.println("wellknown_path: "+wellknown_path);
        } catch (IOException e) {
            System.out.println("error occured while obtaining path for caldav entries.: "+e.getMessage());
            return;
        }
        if (wellknown_path==null) {
            System.out.println("could not determine wellknown_path");
            return;
        }
        ArrayList<HttpUrl> foundCalendarUrls = new ArrayList<>();
        try {
            // 3. get current user principal
            HttpUrl currentUserPrincipal= getCurrentUserPrincipal(HttpUrl.parse(serverURL+wellknown_path));            
            if (currentUserPrincipal==null) {
                System.out.println("could not determine current-user-principal");
                return;
            }
            // 4. get calendar home set
            HttpUrl calendarHomeSet = getCalendarHomeSet(currentUserPrincipal);
            if (currentUserPrincipal==null) {
                System.out.println("could not determine calendar-home-set");
                return;
            }
            // 5. access calendars
            DavCalendar davCalendar = new DavCalendar(httpClient,calendarHomeSet);            
            System.out.println("calendars:");
            davCalendar.propfind(1, new SupportedCalendarComponentSet.Name[0], (response, throwable) -> {
                List<Property> properties = response.getProperties();                                         
                ResourceType resourceType = findResourceType(properties);
                if (resourceType!=null)  {
                    Set<Property.Name> prop = resourceType.getTypes();
                    if (prop.contains(calendarType)) {
                        System.out.println(response.hrefName()+": " + prop.toString());
                        foundCalendarUrls.add(response.getHref());
                    }
                }
            });
        } catch (IOException | DavException e) {
                System.out.println("error occured: "+e.getMessage());
        }
        if (foundCalendarUrls.isEmpty()) { return; }
        // 7. create  calendar entry and upload to the first found calendar
        HttpUrl calendarUrl=foundCalendarUrls.get(0);
        String icsString = createICSString();
        uploadICSString(calendarUrl, icsString);
    }
}


