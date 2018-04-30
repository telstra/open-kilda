/* Copyright 2018 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package org.openkilda.pce.janitor;

import org.apache.commons.cli.*;
import org.neo4j.driver.v1.*;
import sun.misc.BASE64Encoder;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import javax.ws.rs.client.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * FlowJanitor holds methods that facilitate detecting and fixing issues with flows. Examples will
 * be included over time.
 */
public class FlowJanitor {

    /**
     * Use to get the flows that share the same cookie (eg 1 cookie, N flows)
     */
    public static final String DUPLICATE_COOKIES_QUERY =
                    "MATCH (:switch) -[rel:flow]-> (:switch)" +
                            " WITH rel.cookie as affected_cookie, COUNT(rel.cookie) as cookie_num" +
                            " WHERE cookie_num > 1" +
                            " MATCH (:switch) -[rel2:flow]-> (:switch)" +
                            " WHERE rel2.cookie = affected_cookie" +
                            " RETURN affected_cookie, rel2.flowid as affected_flow_id" +
                            " ORDER BY affected_cookie ";

    /**
     * Use to get the flows that share the same transit vlan (eg 1 t-vlan, N flows)
     */
    public static final String DUPLICATE_VLAN_QUERY =
            "MATCH (:switch) -[rel:flow]-> (:switch)" +
                    " WITH rel.transit_vlan as affected_cookie, COUNT(rel.transit_vlan) as cookie_num" +
                    " WHERE cookie_num > 1" +
                    " MATCH (:switch) -[rel2:flow]-> (:switch)" +
                    " WHERE rel2.transit_vlan = affected_cookie and rel2.transit_vlan > 0" +
                    " RETURN affected_cookie, rel.transit_vlan AS transit_vlan, rel2.flowid as affected_flow_id" +
                    " ORDER BY affected_cookie";

    /**
     * Use to get the flows that have multiple instances (ie N flows .. should be just 1 flow)
     */
    public static final String DUPLICATE_FLOWS_QUERY =
            "MATCH (:switch) -[rel:flow]-> (:switch)"+
                    " WITH rel.flowid as affected_flow_id, COUNT(rel.flowid) as flow_num"+
                    " WHERE flow_num > 2"+
                    " MATCH (:switch) -[rel2:flow]-> (:switch)"+
                    " WHERE rel2.flowid = affected_flow_id"+
                    " RETURN affected_flow_id, rel2.cookie as affected_flow_cookie" +
                    " ORDER BY affected_flow_id";

    /**
     * Use to delete a flow (ie in conjunction with DUPLICATE_FLOWS_QUERY)
     */
    public static final String DELETE_DUPLICATE_FLOW =
            "MATCH (:switch) -[rel:flow]-> (:switch)"+
                    " WHERE rel.flowid = %s AND rel.cookie = %d"+
                    " DELETE rel";

    public static final class Config {
        public String neo_url;
        public String neo_user;
        public String neo_pswd;
        public String nb_url;
        public String nb_user;
        public String nb_pswd;
        public String action;
    }


    /**
     * @return the number of cookies that have more than one flow. This shouldn't happen, but this is
     * here to catch scenarios where it does. '2' means two cookies have more than 1 flow each.
     */
    public int CountDuplicateCookies() {
        throw new NotImplementedException(); // TODO: Implement Me
    }

    /**
     * @return the number of cookies that have more than one flow. This shouldn't happen, but this is
     * here to catch scenarios where it does. '2' means two cookies have more than 1 flow each.
     */
    public List<String> FlowsWithDuplicateCookies(boolean verbose) {
        throw new NotImplementedException(); // TODO: Implement Me
    }


    public static final void updateFlows(FlowJanitor.Config config, List<String> flowsToUpdate) {
        String authString = config.nb_user + ":" + config.nb_pswd;
        String authStringEnc = new BASE64Encoder().encode(authString.getBytes());

        Client client = ClientBuilder.newClient();

        for (String flowid : flowsToUpdate){
            /*
             * Get the Flows .. call NB for each
             */
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (Exception e) {}

            System.out.println("RUNNING: flowid = " + flowid);

            WebTarget webTarget = client.target(config.nb_url + "/api/v1/flows").path(flowid);
            Invocation.Builder invocationBuilder =
                    webTarget.request(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Basic " + authStringEnc);

            Response response = invocationBuilder.get(Response.class);

            if (response.getStatus() != 200) {
                throw new RuntimeException("Failed : HTTP error code : "
                        + response.getStatus());
            }

            String output = response.readEntity(String.class);

            /*
             * Call update Flow .. add to description
             */
            String[] split = output.split("PUSHED FLOW");
            if (split.length == 2)
                output = split[0] + "PUSHED FLOW. FIX cookie dupe." + split[1];


            // LOOP
            response = invocationBuilder.put(Entity.entity(output,MediaType.APPLICATION_JSON));

            if (response.getStatus() != 200) {
                System.out.println("FAILURE: flowid = " + flowid + "; response = " + response.getStatus());
            }
        }
    }

    /**
     * With the right URL, username, password, and method keyword, the methods above can be called.
     *
     * @param args
     */
    public static void main(String[] args) {
        Options options = new Options();
        options.addOption(Option.builder("url").required(true).hasArg()
                .desc("The URL of the Neo4J DB - i.e. bolt://neo..:7474").build());
        options.addOption(Option.builder("u").required(true).hasArg().longOpt("user")
                .desc("The Neo4J username - e.g. neo4j").build());
        options.addOption(Option.builder("p").required(true).hasArg().longOpt("password")
                .desc("The Neo4J password - e.g. neo4j").build());

        options.addOption(Option.builder("nburl").required(true).hasArg()
                .desc("The URL of the Neo4J DB - i.e. http://northboud..:8080").build());
        options.addOption(Option.builder("nbu").required(true).hasArg().longOpt("user")
                .desc("The Neo4J username - e.g. kilda").build());
        options.addOption(Option.builder("nbp").required(true).hasArg().longOpt("password")
                .desc("The Neo4J password - e.g. kilda").build());

        options.addOption(Option.builder("a").required(true).hasArg().longOpt("action")
                .desc("The action to take - e.g. CountDuplicateCookies").build());
        options.addOption(Option.builder("v").required(false).longOpt("verbose")
                .desc("Where appropriate, return a verbose response").build());

        CommandLine commandLine;
        CommandLineParser parser = new DefaultParser();
        Driver driver = null;

        try {
            commandLine = parser.parse(options, args);
            FlowJanitor.Config config = new FlowJanitor.Config();
            config.neo_url = commandLine.getOptionValue("url");
            config.neo_user = commandLine.getOptionValue("u");
            config.neo_pswd = commandLine.getOptionValue("p");
            config.nb_url = commandLine.getOptionValue("nburl");
            config.nb_user = commandLine.getOptionValue("nbu");
            config.nb_pswd = commandLine.getOptionValue("nbp");
            config.action = commandLine.getOptionValue("a");

            driver = GraphDatabase.driver(config.neo_url, AuthTokens.basic(config.neo_user, config.neo_pswd));

            if (config.action.equals("DeDupeFlows")) {

                Session session = driver.session();
                StatementResult result = session.run(DUPLICATE_FLOWS_QUERY);
                Map<String,List<Long>> flowsToUpdate = new HashMap<>();
                for (Record record : result.list()) {
                    String flowid = record.get("affected_flow_id").asString();
                    List<Long> priors = flowsToUpdate.computeIfAbsent(flowid, empty -> new ArrayList<>());
                    priors.add(record.get("affected_flow_cookie").asLong());
                }
                session.close();
                System.out.println("flowsToUpdate.size() = " + flowsToUpdate.size());
                System.out.println("flowsToUpdate = " + flowsToUpdate);

                System.out.println("Will De-Dupe the Flows");

                String authString = config.nb_user + ":" + config.nb_pswd;
                String authStringEnc = new BASE64Encoder().encode(authString.getBytes());
                Client client = ClientBuilder.newClient();

                for (String flowid : flowsToUpdate.keySet()){
                    /*
                     * Get the Flows .. call NB for each
                     */
                    try {
                        TimeUnit.SECONDS.sleep(1);
                    } catch (Exception e) {}

                    System.out.println("RUNNING: flowid = " + flowid);

                    WebTarget webTarget = client.target(config.nb_url + "flows").path(flowid);
                    Invocation.Builder invocationBuilder =
                            webTarget.request(MediaType.APPLICATION_JSON)
                                    .header("Authorization", "Basic " + authStringEnc);

                    Response response = invocationBuilder.get(Response.class);

                    if (response.getStatus() != 200) {
                        throw new RuntimeException("Failed : HTTP error code : "
                                + response.getStatus());
                    }

                    String output = response.readEntity(String.class);
                    System.out.println("output = " + output);
                    System.exit(0);
                    
                }

            } else {
                // TODO: switch, based on action
                Session session = driver.session();
                StatementResult result = session.run(DUPLICATE_COOKIES_QUERY);
                List<String> flowsToUpdate = new ArrayList<>();
                for (Record record : result.list()) {
                    flowsToUpdate.add(record.get("affected_flow_id").asString());
                }
                session.close();
                System.out.println("flowsToUpdate.size() = " + flowsToUpdate.size());
                System.out.println("flowsToUpdate = " + flowsToUpdate);

                System.exit(0);
                FlowJanitor.updateFlows(config, flowsToUpdate);

            }
//            NeoDriver target = new NeoDriver(driver);
//            System.out.println("target.getSwitches() = " + target.getSwitches());

        } catch (ParseException exception) {
            System.out.print("Parse error: ");
            System.out.println(exception.getMessage());
            // automatically generate the help statement
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( "FlowJanitor", options );
        } finally {
            if (driver != null)
                driver.close();
        }

    }
}
