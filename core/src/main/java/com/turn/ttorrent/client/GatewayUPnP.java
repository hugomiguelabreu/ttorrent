package com.turn.ttorrent.client;

import org.bitlet.weupnp.GatewayDevice;
import org.bitlet.weupnp.GatewayDiscover;
import org.bitlet.weupnp.PortMappingEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;

public final class GatewayUPnP {

    private static boolean LIST_ALL_MAPPINGS = true;
    private static GatewayDiscover gatewayDiscover = null;
    private static Map<InetAddress, GatewayDevice> gateways = null;
    private static InetAddress localAddress;
    private static String externalIPAddress;
    private static GatewayDevice activeGW = null;
    private static PortMappingEntry portMapping;
    private static final Logger logger =
            LoggerFactory.getLogger(GatewayUPnP.class);

    private GatewayUPnP() throws InterruptedException
    {
    }

    public static boolean startGatewayDiscover() throws ParserConfigurationException, SAXException, IOException, InterruptedException
    {
        if(activeGW != null && activeGW.isConnected())
            return true;

        gatewayDiscover = new GatewayDiscover();
        gateways = gatewayDiscover.discover();

        if (gateways.isEmpty()) {
            logger.warn("No gateways found");
            logger.warn("Stopping weupnp");
            return false;
        }

        logger.info(gateways.size()+" gateway(s) found\n");

        int counter=0;
        for (GatewayDevice gw: gateways.values()) {
            counter++;
            logger.info("Listing gateway details of device #" + counter+
                    "\n\tFriendly name: " + gw.getFriendlyName()+
                    "\n\tPresentation URL: " + gw.getPresentationURL()+
                    "\n\tModel name: " + gw.getModelName()+
                    "\n\tModel number: " + gw.getModelNumber()+
                    "\n\tLocal interface address: " + gw.getLocalAddress().getHostAddress()+"\n");
        }
        // choose the first active gateway for the tests
        activeGW = gatewayDiscover.getValidGateway();

        if (null != activeGW) {
            logger.info("Using gateway: " + activeGW.getFriendlyName());
        } else {
            logger.warn("No active gateway device found");
            logger.warn("Stopping weupnp");
            return false;
        }

        // testing PortMappingNumberOfEntries
        Integer portMapCount = activeGW.getPortMappingNumberOfEntries();
        logger.info("GetPortMappingNumberOfEntries: " + (portMapCount!=null?portMapCount.toString():"(unsupported)"));

        // testing getGenericPortMappingEntry
        portMapping = new PortMappingEntry();
        if (LIST_ALL_MAPPINGS) {
            int pmCount = 0;
            do {
                if (activeGW.getGenericPortMappingEntry(pmCount,portMapping))
                    logger.info("Portmapping #"+pmCount+" successfully retrieved ("+portMapping.getPortMappingDescription()+":"+portMapping.getExternalPort()+")");
                else{
                    logger.warn("Portmapping #"+pmCount+" retrieval failed");
                    break;
                }
                pmCount++;
            } while (portMapping!=null);
        } else {
            if (activeGW.getGenericPortMappingEntry(0,portMapping))
                logger.info("Portmapping #0 successfully retrieved ("+portMapping.getPortMappingDescription()+":"+portMapping.getExternalPort()+")");
            else
                logger.warn("Portmapping #0 retrival failed");
        }

        localAddress = activeGW.getLocalAddress();
        logger.info("Using local address: "+ localAddress.getHostAddress());
        externalIPAddress = activeGW.getExternalIPAddress();
        logger.info("External address: "+ externalIPAddress);

        return true;
    }

    public static boolean mapPort(int port) throws IOException, SAXException
    {
        logger.info("Querying device to see if a port mapping already exists for port "+ port);

        if (activeGW.getSpecificPortMappingEntry(port,"TCP",portMapping)) {
            logger.warn("Port "+port+" is already mapped. Aborting test.");
            return false;
        } else {
            logger.info("Mapping free. Sending port mapping request for port "+port);
            if (!activeGW.addPortMapping(port,port,localAddress.getHostAddress(),"TCP","test")) {
                logger.warn("Mapping UNSUCCESSFUL.");
                return false;
            }
        }

        logger.info("Mapping SUCCESSFUL.");
        return true;
    }

    public static boolean removePort(int port) throws IOException, SAXException
    {
        if (activeGW.deletePortMapping(port,"TCP")) {
            logger.info("Port mapping removed");
            return true;
        } else {
            logger.warn("Port mapping removal FAILED");
            return false;
        }
    }
}
