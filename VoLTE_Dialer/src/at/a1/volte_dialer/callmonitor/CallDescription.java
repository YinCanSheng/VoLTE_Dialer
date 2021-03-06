/**
 *  Part of the dialer for testing VoLTE network side KPIs.
 *  
 *   Copyright (C) 2014  Spinlogic
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2 as 
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package at.a1.volte_dialer.callmonitor;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.spinlogic.logger.SP_Logger;
import android.content.Context;
import android.telephony.CellIdentityCdma;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.CellLocation;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;

public class CallDescription {
	private final String TAG = "CallDescription";
	private final static Logger LOGGER = Logger.getLogger(SP_Logger.LOGGER_NAME);
	
	private static final String ACCESS_UNKNOWN	= "Unknown";
	private static final String ACCESS_LTE		= "LTE";
	private static final String ACCESS_WCDMA	= "WCDMA";
	private static final String ACCESS_GSM		= "GSM";
	private static final String ACCESS_CDMA		= "CDMA";
	
	// Call disconnection options
	public static final String CALL_DISCONNECTED_BY_UE 	= "UE";
	public static final String CALL_DISCONNECTED_BY_NW 	= "NW";
	public static final String CALL_DISCONNECTED_BY_UNK = "UNK";	// Unknown. E.g. when the app runs in background.
	
	// Call direction options
	public static final String MT_CALL	= "MT";
	public static final String MO_CALL	= "MO";
	
	private Context context;
	private long	starttime;
	private long	alertingtime;		// 18X received
	private long	activetime;			// 200 received
	private long	endtime;
	private String	direction;			// MO or MT
	private String	prefix;				// first six digits of the msisdn
	private String  disconnectionside;  // UE, NW
	private String	disconnectioncause; // refer to DisconnectCause in com.android.internal.telephony.Connection
	private String	startcellinfo;
	private String	endcellinfo;
	private int		startsignalstrength;
	private int		endsignalstrength;
	private long 	srvcc;				// timestamp at which SRVCC occurred, set to zero if it did not

	/**
	 * Constructor.
	 * 
	 * @param scid	Cell Id when call is started
	 */
	public CallDescription(Context c, String dir, int strength) {
		context 			= c;
		starttime			= System.currentTimeMillis();
		alertingtime		= 0;
		activetime			= 0;
		endtime				= 0;
		direction			= dir;
		prefix				= "";
		disconnectionside	= "UNKNOWN";
		disconnectioncause	= "UNKNOWN";
		startcellinfo		= getCurrentCellId();
		endcellinfo 		= "";
		startsignalstrength	= strength;
		endsignalstrength	= 99;	// unknown
		srvcc				= 0;
		LOGGER.setLevel(Level.INFO);
	}
	
	/**
	 * Records data when the call is disconnected.
	 * 
	 * @param ds	disconnection side
	 * 
	 */
	public void endCall(String ds, int strength) {
		disconnectionside	= ds;
		endtime 			= System.currentTimeMillis();
		endcellinfo 		= getCurrentCellId();
		endsignalstrength	= strength;
	}
	
	/**
	 * Writes a log entry for the call in the log file.
	 */
	public void writeCallInfoToLog() {
		long duration 			= (activetime > 0) ? 
								  ((endtime - activetime)) : 
								  ((endtime - starttime));
		long callalertedtime 	= (alertingtime > 0) ? 
								  ((alertingtime - starttime)) : 
								  0;
		long callconnectedtime	= (activetime > 0) ? 
								  ((activetime - starttime)) : 
								  0;
		long srvcctime			= (srvcc > 0) ? 
								  ((srvcc - starttime)) : 
								  0;
		String logline = direction								+ CallLogger.CSV_CHAR +
						 prefix									+ CallLogger.CSV_CHAR +
						 Long.toString(duration) 				+ CallLogger.CSV_CHAR +
						 Long.toString(callalertedtime)			+ CallLogger.CSV_CHAR +
						 Long.toString(callconnectedtime) 		+ CallLogger.CSV_CHAR +
						 disconnectionside 						+ CallLogger.CSV_CHAR +
						 disconnectioncause						+ CallLogger.CSV_CHAR +
						 startcellinfo 							+ CallLogger.CSV_CHAR +
						 Integer.toString(startsignalstrength)	+ CallLogger.CSV_CHAR +
						 endcellinfo							+ CallLogger.CSV_CHAR +
						 Integer.toString(endsignalstrength)	+ CallLogger.CSV_CHAR +
						 Long.toString(srvcctime);
		CallLogger.appendLog(logline, starttime);
	}
	
	public void setPrefix(String msisdn_prefix) {
		prefix = msisdn_prefix;
	}
	
	public void setAlertingTime() {
		alertingtime = System.currentTimeMillis();
	}
	
	public void setActiveTime() {
		activetime = System.currentTimeMillis();
	}
	
	public void setDirection(String dir) {
		direction = dir;
	}
	
	public void setSrvccTime() {
		srvcc = System.currentTimeMillis();
	}
	
	public void setDisconnectionCause(String cause) {
		disconnectioncause = cause;
	}
	
	public boolean isDisconnectionCauseKnown() {
		return (disconnectioncause != "UNKNOWN");
	}
	
	// PRIVATE METHODS
	
	/**
	 * Gets info about the cell service the UE.
	 * It does not work for UEs that are only registered for LTE.
	 * 
	 * @param context
	 * @return	String with the following information:
	 * 		3GPP (WCDMA or GSM) / 3GPP2 (CDMA)	+ delimiter + 
	 * 			
	 * 		For LTE:
	 * 			 	MCC + delimiter + MNC + delimiter + TAC + delimiter + CID + delimiter + PCI
	 * 		For WCDMA:
	 * 				MCC + delimiter + MNC + delimiter + LAC + delimiter + CID + delimiter + PSC
	 * 		For GSM:
	 * 				MCC + delimiter + MNC + delimiter + LAC + delimiter + CID	
	 * 		For CDMA:
	 * 				System Id + delimiter + Network Id + delimiter + Base Station Id
	 * 
	 * NOTE: 	An alternative to this method is to listen for PhoneStateListener:onCellInfoChange,
	 * 			but we do not really care about cells added and removed from the neighbouring cell
	 * 			list. We only care for the current cell.
	 * 				
	 */
	private String getCurrentCellId() {
		final String METHOD = " getCurrentCellId()  ";
		final String DELIMITER = "_";
		
		String returnvalue = "";
		
		TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
		List<CellInfo> cellInfoList = tm.getAllCellInfo();
		
		if(cellInfoList != null) {
			for(CellInfo cellInfo : cellInfoList) {	// Requires API >= 17
				if(cellInfo instanceof CellInfoLte) {
					CellIdentityLte ci = ((CellInfoLte) cellInfo).getCellIdentity();
			    	returnvalue = ACCESS_LTE + DELIMITER +
			    				  Integer.toString(ci.getMcc()) + DELIMITER +
			    				  Integer.toString(ci.getMnc()) + DELIMITER +
			    				  Integer.toString(ci.getTac()) + DELIMITER +
			    				  Integer.toString(ci.getCi()) + DELIMITER +
			    				  Integer.toString(ci.getPci());
				} else if(cellInfo instanceof CellInfoWcdma) {	// Requires API >= 18
					CellIdentityWcdma ci = ((CellInfoWcdma) cellInfo).getCellIdentity();
					returnvalue = ACCESS_WCDMA + DELIMITER +
		    				  Integer.toString(ci.getMcc()) + DELIMITER +
		    				  Integer.toString(ci.getMnc()) + DELIMITER +
		    				  Integer.toString(ci.getLac()) + DELIMITER +
		    				  Integer.toString(ci.getCid()) + DELIMITER +
		    				  Integer.toString(ci.getPsc());
				} else if(cellInfo instanceof CellInfoGsm) {	// Requires API >= 17
					CellIdentityGsm ci = ((CellInfoGsm) cellInfo).getCellIdentity();
					returnvalue = ACCESS_GSM + DELIMITER +
		    				  Integer.toString(ci.getMcc()) + DELIMITER +
		    				  Integer.toString(ci.getMnc()) + DELIMITER +
		    				  Integer.toString(ci.getLac()) + DELIMITER +
		    				  Integer.toString(ci.getCid());
				} else if(cellInfo instanceof CellInfoCdma) {	// Requires API >= 17
					CellIdentityCdma ci = ((CellInfoCdma) cellInfo).getCellIdentity();
					returnvalue = ACCESS_CDMA + DELIMITER +
		    				  Integer.toString(ci.getSystemId()) + DELIMITER +
		    				  Integer.toString(ci.getNetworkId()) + DELIMITER +
		    				  Integer.toString(ci.getBasestationId());
				}
				break;	// Assume that first cell in the list is the current one.
						// We do not care about neighbouring ones.
			}
		}
		else {
			// Most likely getAllCellInfo() has an empty implementation in this UE
			// We can still get the cell info, but we do not know which radio (LTE, WCDMA, GSM, CDMA)
			// TODO: obtain cell info via reflection using mPhone in PreciseCallEventsHandler
		    CellLocation cl = tm.getCellLocation();
		    GsmCellLocation gsmLoc;
	        CdmaCellLocation cdmaLoc;
	        try {
	            String networkOperator = tm.getNetworkOperator();
	            if(networkOperator != null) {
	            returnvalue = networkOperator.substring(0, 3) + DELIMITER +
	            			  networkOperator.substring(3) + DELIMITER;
	    	    }
	            gsmLoc = (GsmCellLocation) cl;
	            returnvalue +=  String.valueOf(gsmLoc.getLac()) + DELIMITER;
	            returnvalue +=  String.valueOf(gsmLoc.getCid());
	        } catch (ClassCastException e) {
	        	try {
		            cdmaLoc = (CdmaCellLocation) cl;
		            returnvalue = ACCESS_CDMA + DELIMITER + String.valueOf(cdmaLoc.getSystemId()) + DELIMITER;
		            returnvalue +=  String.valueOf(cdmaLoc.getNetworkId()) + DELIMITER;
		            returnvalue +=  String.valueOf(cdmaLoc.getBaseStationId());
	        	} catch(ClassCastException ex) {
	        		returnvalue +=  ACCESS_UNKNOWN;
//	        		Logger.Log(TAG + METHOD, ex.getMessage());
	        		LOGGER.info(TAG + METHOD + ex.getClass().getName() + ex.toString());
	        	}
	        }
		}
        return returnvalue;
	}
}
