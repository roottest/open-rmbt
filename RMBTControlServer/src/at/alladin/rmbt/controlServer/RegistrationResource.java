/*******************************************************************************
 * Copyright 2013-2014 alladin-IT GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package at.alladin.rmbt.controlServer;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.json.JSONException;
import org.json.JSONObject;
import org.restlet.data.Reference;
import org.restlet.resource.Get;
import org.restlet.resource.Post;

import at.alladin.rmbt.db.Client;
import at.alladin.rmbt.db.GeoLocation;
import at.alladin.rmbt.db.Test_Server;
import at.alladin.rmbt.shared.GeoIPHelper;
import at.alladin.rmbt.shared.Helperfunctions;
import at.alladin.rmbt.shared.ResourceManager;

import com.google.common.net.InetAddresses;

public class RegistrationResource extends ServerResource
{
    @Post("json")
    public String request(final String entity)
    {
        final String secret = getContext().getParameters().getFirstValue("RMBT_SECRETKEY");
        
        addAllowOrigin();
        
        JSONObject request = null;
        
        final ErrorList errorList = new ErrorList();
        final JSONObject answer = new JSONObject();
        String answerString;
        
        final String clientIpRaw = getIP();
        final InetAddress clientAddress = InetAddresses.forString(clientIpRaw);
        final String clientIpString = InetAddresses.toAddrString(clientAddress);
        
        System.out.println(MessageFormat.format(labels.getString("NEW_REQUEST"), clientIpRaw));
        
        if (entity != null && !entity.isEmpty())
            // try parse the string to a JSON object
            try
            {
                request = new JSONObject(entity);
                
                int typeId = 0;
                
                final String lang = request.optString("language");
                
                // Load Language Files for Client
                
                final List<String> langs = Arrays.asList(settings.getString("RMBT_SUPPORTED_LANGUAGES").split(",\\s*"));
                
                if (langs.contains(lang))
                {
                    errorList.setLanguage(lang);
                    labels = ResourceManager.getSysMsgBundle(new Locale(lang));
                }
                
//                System.out.println(request.toString(4));
                
                if (conn != null)
                {
                    
                    final Client clientDb = new Client(conn);
                    
                    if (!request.optString("type").isEmpty())
                    {
                        typeId = clientDb.getTypeId(request.getString("type"));
                        if (clientDb.hasError())
                            errorList.addError(clientDb.getError());
                    }
                    
                    final List<String> clientNames = Arrays.asList(settings.getString("RMBT_CLIENT_NAME")
                            .split(",\\s*"));
                    final List<String> clientVersions = Arrays.asList(settings.getString("RMBT_VERSION_NUMBER").split(
                            ",\\s*"));
                    
                    if (clientNames.contains(request.optString("client"))
                            && clientVersions.contains(request.optString("version")) && typeId > 0)
                    {
                        
                        UUID uuid = null;
                        final String uuidString = request.optString("uuid", "");
                        if (uuidString.length() != 0)
                            uuid = UUID.fromString(uuidString);
                        
                        final String clientName = request.getString("client");
                        final String clientVersion = request.getString("version");
                        
                        String timeZoneId = request.getString("timezone");
                        // String tmpTimeZoneId = timeZoneId;
                        
                        final long clientTime = request.getLong("time");
                        final Timestamp clientTstamp = java.sql.Timestamp.valueOf(new Timestamp(clientTime).toString());
                        
                        final JSONObject location = request.optJSONObject("location");
                        
                        long geotime = 0;
                        double geolat = 0;
                        double geolong = 0;
                        float geoaccuracy = 0;
                        double geoaltitude = 0;
                        float geobearing = 0;
                        float geospeed = 0;
                        String geoprovider = "";
                        
                        if (!request.isNull("location"))
                        {
                            geotime = location.optLong("time", 0);
                            geolat = location.optDouble("lat", 0);
                            geolong = location.optDouble("long", 0);
                            geoaccuracy = (float) location.optDouble("accuracy", 0);
                            geoaltitude = location.optDouble("altitude", 0);
                            geobearing = (float) location.optDouble("bearing", 0);
                            geospeed = (float) location.optDouble("speed", 0);
                            geoprovider = location.optString("provider", "");
                        }
                        
                        Calendar timeWithZone = null;
                        
                        if (timeZoneId.isEmpty())
                        {
                            timeZoneId = Helperfunctions.getTimezoneId();
                            timeWithZone = Helperfunctions.getTimeWithTimeZone(timeZoneId);
                        }
                        else
                            timeWithZone = Helperfunctions.getTimeWithTimeZone(timeZoneId);
                        
                        long clientUid = 0;
                        /*
                         * if (uuid == null) {
                         * clientDb.setTimeZone(timeWithZone);
                         * clientDb.setTime(tstamp);
                         * clientDb.setClient_type_id(typeId); uuid =
                         * clientDb.storeClient(); if (clientDb.hasError()) {
                         * errorList.addError(clientDb.getError()); } else {
                         * answer.put("uuid", uuid.toString()); } }
                         */
                        
                        if (errorList.getLength() == 0 && uuid != null)
                        {
                            clientUid = clientDb.getClientByUuid(uuid);
                            if (clientDb.hasError())
                                errorList.addError(clientDb.getError());
                        }
                        
                        if (clientUid > 0)
                        {
                            
                            final String testUuid = UUID.randomUUID().toString();
                            final String testOpenUuid = UUID.randomUUID().toString();
                            
                            int testServerId = 0;
                            int testServerPort = 0;
                            String testServerAddress = "";
                            String testServerName = "";
                            boolean testServerEncryption = true; // default is
                                                                 // true
                            
                            final Test_Server server;
                            if (request.optString("client").equals("RMBTws")) {
                            	server = getNearestServer(errorList, geolat, geolong, geotime, clientIpString, true);
                            }
                            else {
                            	server = getNearestServer(errorList, geolat, geolong, geotime, clientIpString, false);
                            }
                            
                            
                            if (server != null)
                            {
                                
                                testServerId = server.getUid();
                                
                                if (clientAddress instanceof Inet6Address)
                                    testServerAddress = server.getWeb_address_ipv6();
                                else if (clientAddress instanceof Inet4Address)
                                    testServerAddress = server.getWeb_address_ipv4();
                                else
                                    testServerAddress = server.getWeb_address(); // does
                                                                                 // this
                                                                                 // really
                                                                                 // make
                                                                                 // sense?
                                                                                 // ;)
                                    
                                // hack for android api <= 10 (2.3.x)
                                // using encryption with test doesn't work
                                if (request.has("plattform") && request.optString("plattform").equals("Android"))
                                    if (request.has("api_level"))
                                    {
                                        final String apiLevelString = request.optString("api_level");
                                        try
                                        {
                                            final int apiLevel = Integer.parseInt(apiLevelString);
                                            if (apiLevel <= 10)
                                                testServerEncryption = false;
                                        }
                                        catch (final NumberFormatException e)
                                        {
                                        }
                                    }
                                
                                // // DEBUG
                                // testServerEncryption = false;
                                
                                // request.optString("plattform");
                                if (testServerEncryption)
                                    testServerPort = server.getPort_ssl();
                                else
                                    testServerPort = server.getPort();
                                
                                testServerName = server.getName() + " (" + server.getCity() + " / "
                                        + server.getCountry() + ")";
                                
                            }
                            else
                                errorList.addError("ERROR_TEST_SERVER");
                            
                            try
                            {
                                if (timeZoneId.isEmpty())
                                {
                                    timeZoneId = Helperfunctions.getTimezoneId();
                                    timeWithZone = Helperfunctions.getTimeWithTimeZone(timeZoneId);
                                }
                                else
                                    timeWithZone = Helperfunctions.getTimeWithTimeZone(timeZoneId);
                                
                                answer.put("test_server_address", testServerAddress);
                                answer.put("test_server_port", testServerPort);
                                answer.put("test_server_name", testServerName);
                                answer.put("test_server_encryption", testServerEncryption);
                                
                                answer.put("test_duration", settings.getString("RMBT_DURATION"));
                                answer.put("test_numthreads", settings.getString("RMBT_NUM_THREADS"));
                                
                                answer.put("client_remote_ip", clientIpString);
                                
                                final String resultUrl = new Reference(getURL(), settings.getString("RMBT_RESULT_PATH"))
                                        .getTargetRef().toString();
                                
                                // System.out.println(resultUrl);
                                
                                answer.put("result_url", resultUrl);
                                
                                final String resultQoSUrl = new Reference(getURL(), settings.getString("RMBT_QOS_RESULT_PATH")).getTargetRef().toString();
                        
                                // System.out.println(resultUrl);
                        
                                answer.put("result_qos_url", resultQoSUrl);
                            }
                            catch (final JSONException e)
                            {
                                System.out.println("Error generating Answer " + e.toString());
                                errorList.addError("ERROR_RESPONSE_JSON");
                                
                            }
                            
                            if (errorList.getLength() == 0)
                                try
                                {
                                    
                                    PreparedStatement st;
                                    st = conn
                                            .prepareStatement(
                                                    "INSERT INTO test(time, uuid, open_test_uuid, client_id, client_name, client_version, client_software_version, client_language, client_public_ip, client_public_ip_anonymized, country_geoip, server_id, port, use_ssl, timezone, client_time, duration, num_threads_requested, status, software_revision, client_test_counter, client_previous_test_status, public_ip_asn, public_ip_as_name, country_asn, public_ip_rdns, run_ndt)"
                                                            + "VALUES(NOW(), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                                                    Statement.RETURN_GENERATED_KEYS);
                                    
                                    int i = 1;
                                    // uuid
                                    st.setObject(i++, UUID.fromString(testUuid));
                                    // open_test_uuid
                                    st.setObject(i++, UUID.fromString(testOpenUuid));
                                    // client_id
                                    st.setLong(i++, clientUid);
                                    // client_name
                                    st.setString(i++, clientName);
                                    // client_version
                                    st.setString(i++, clientVersion);
                                    // client_software_version
                                    st.setString(i++, request.optString("softwareVersion", null));
                                    // client_language
                                    st.setString(i++, lang);
                                    // client_public_ip
                                    st.setString(i++, clientIpString);
                                    // client_public_ip_anonymized
                                    st.setString(i++, Helperfunctions.anonymizeIp(clientAddress));
                                    // country_geoip (2digit country code derived from public IP of client)
                                    st.setString(i++, GeoIPHelper.lookupCountry(clientAddress));
                                    // server_id
                                    st.setInt(i++, testServerId);
                                    // port
                                    st.setInt(i++, testServerPort);
                                    // use_ssl
                                    st.setBoolean(i++, testServerEncryption);
                                    // timezone (of client)
                                    st.setString(i++, timeZoneId);
                                    // client_time (local time of client)
                                    st.setTimestamp(i++, clientTstamp, timeWithZone);
                                    // duration (requested)
                                    st.setInt(i++, Integer.parseInt(settings.getString("RMBT_DURATION")));
                                    // num_threads_requested 
                                    st.setInt(i++, Integer.parseInt(settings.getString("RMBT_NUM_THREADS")));
                                    // status (of test)
                                    st.setString(i++, "STARTED"); //was "RUNNING" before
                                    // software_revision (of client)
                                    st.setString(i++, request.optString("softwareRevision", null));
                                    // client_test_counter (number of tests the client has performed)
                                    final int testCounter = request.optInt("testCounter", -1);
                                    if (testCounter == -1) // older clients did not support testCounter
                                        st.setNull(i++, Types.INTEGER);
                                    else
                                        st.setLong(i++, testCounter);
                                    // client_previous_test_status (outcome of previous test)
                                    st.setString(i++, request.optString("previousTestStatus", null));
                                    // public_ip_asn
                                    final Long asn = Helperfunctions.getASN(clientAddress);
                                    // public_ip_as_name 
                                    // country_asn (2 digit country code of AS, eg. AT or EU)
                                    final String asName;
                                    final String asCountry;
                                    if (asn == null) {
                                        st.setNull(i++, Types.BIGINT);
                                        asName = null;
                                        asCountry =null;
                                    }
                                    else {
                                        st.setLong(i++, asn);
                                        asName = Helperfunctions.getASName(asn);
                                        asCountry = Helperfunctions.getAScountry(asn);
                                    }
                                    if (asName == null)
                                        st.setNull(i++, Types.VARCHAR);
                                    else
                                        st.setString(i++, asName);
                                    if (asCountry == null)
                                        st.setNull(i++, Types.VARCHAR);
                                    else
                                        st.setString(i++, asCountry);    
                                    //public_ip_rdns
                                    String reverseDNS = Helperfunctions.reverseDNSLookup(clientAddress);
                                    if (reverseDNS == null || reverseDNS.isEmpty())
                                        st.setNull(i++, Types.VARCHAR);
                                    else
                                    {
                                        reverseDNS = reverseDNS.replaceFirst("\\.$", "");
                                        st.setString(i++, reverseDNS); // cut off last dot (#332)
                                    }
                                    // run_ndt
                                    if (request.has("ndt"))
                                        st.setBoolean(i++, request.getBoolean("ndt"));
                                    else
                                        st.setNull(i++, Types.BOOLEAN);
                                    
                                    final int affectedRows = st.executeUpdate();
                                    if (affectedRows == 0)
                                        errorList.addError("ERROR_DB_STORE_TEST");
                                    else
                                    {
                                        long key = 0;
                                        final ResultSet rs = st.getGeneratedKeys();
                                        if (rs.next())
                                            // Retrieve the auto generated
                                            // key(s).
                                            key = rs.getLong(1);
                                        rs.close();
                                        
                                        final PreparedStatement getProviderSt = conn
                                                .prepareStatement("SELECT rmbt_set_provider_from_as(?)");
                                        getProviderSt.setLong(1, key);
                                        String provider = null;
                                        if (getProviderSt.execute())
                                        {
                                            final ResultSet rs2 = getProviderSt.getResultSet();
                                            if (rs2.next())
                                                provider = rs2.getString(1);
                                        }
                                        
                                        if (provider != null)
                                            answer.put("provider", provider);
                                        
                                        final PreparedStatement testSlotStatement = conn
                                                .prepareStatement("SELECT rmbt_get_next_test_slot(?)");
                                        testSlotStatement.setLong(1, key);
                                        int testSlot = -1;
                                        if (testSlotStatement.execute())
                                        {
                                            final ResultSet rs2 = testSlotStatement.getResultSet();
                                            if (rs2.next())
                                                testSlot = rs2.getInt(1);
                                        }
                                        
                                        if (testSlot < 0)
                                            errorList.addError("ERROR_DB_STORE_GENERAL");
                                        else
                                        {
                                            final String data = testUuid + "_" + testSlot;
                                            final String hmac = Helperfunctions.calculateHMAC(secret, data);
                                            if (hmac.length() == 0)
                                                errorList.addError("ERROR_TEST_TOKEN");
                                            final String token = data + "_" + hmac;
                                            
                                            final PreparedStatement updateSt = conn
                                                    .prepareStatement("UPDATE test SET token = ? WHERE uid = ?");
                                            updateSt.setString(1, token);
                                            updateSt.setLong(2, key);
                                            updateSt.executeUpdate();
                                            
                                            answer.put("test_token", token);
                                            
                                            answer.put("test_uuid", testUuid);
                                            answer.put("test_id", key);
                                            
                                            final long now = System.currentTimeMillis();
                                            int wait = testSlot - (int) (now / 1000);
                                            if (wait < 0)
                                                wait = 0;
                                            
                                            answer.put("test_wait", wait);
                                            
                                            if (geotime != 0 && geolat != 0 && geolong != 0)
                                            {
                                                
                                                final GeoLocation clientLocation = new GeoLocation(conn);
                                                
                                                clientLocation.setTest_id(key);
                                                
                                                final Timestamp geotstamp = java.sql.Timestamp.valueOf(new Timestamp(
                                                        geotime).toString());
                                                clientLocation.setTime(geotstamp, timeZoneId);
                                                
                                                clientLocation.setAccuracy(geoaccuracy);
                                                clientLocation.setAltitude(geoaltitude);
                                                clientLocation.setBearing(geobearing);
                                                clientLocation.setSpeed(geospeed);
                                                clientLocation.setProvider(geoprovider);
                                                clientLocation.setGeo_lat(geolat);
                                                clientLocation.setGeo_long(geolong);
                                                
                                                clientLocation.storeLocation();
                                                
                                                if (clientLocation.hasError())
                                                    errorList.addError(clientLocation.getError());
                                            }
                                        }
                                    }
                                    
                                    st.close();
                                }
                                catch (final SQLException e)
                                {
                                    errorList.addError("ERROR_DB_STORE_GENERAL");
                                    e.printStackTrace();
                                    
                                }
                            
                        }
                        else
                            errorList.addError("ERROR_CLIENT_UUID");
                        
                    }
                    else
                        errorList.addError("ERROR_CLIENT_VERSION");
                    
                }
                else
                    errorList.addError("ERROR_DB_CONNECTION");
//                System.out.println(answer.toString(4));
            }
            catch (final JSONException e)
            {
                errorList.addError("ERROR_REQUEST_JSON");
                System.out.println("Error parsing JSDON Data " + e.toString());
            }
        else
            errorList.addErrorString("Expected request is missing.");
        
        try
        {
            answer.putOpt("error", errorList.getList());
        }
        catch (final JSONException e)
        {
            System.out.println("Error saving ErrorList: " + e.toString());
        }
        
        answerString = answer.toString();
        return answerString;
    }
    
    @Get("json")
    public String retrieve(final String entity)
    {
        return request(entity);
    }
    
    /**
     * @param geolat
     * @param geolong
     * @param geotime
     * @param clientIp
     * @return
     */
    private Test_Server getNearestServer(final ErrorList errorList, final double geolat, final double geolong,
            final long geotime, final String clientIp, final boolean websocket)
    {
        
        // TODO find nearest Server to GeoLocation or IP address
        
        final Test_Server result = new Test_Server(conn);
        if (websocket) {
        	result.getServerForWebsocketConnection();
        }
        else {
        	result.getServerByUid(1);
        }
        if (result.hasError())
        {
            errorList.addError(result.getError());
            return null;
        }
        else if (result.getUid() == 0)
            return null;
        else
            return result;
    }
    
}
