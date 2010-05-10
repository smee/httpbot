/*
 * ====================================================================
 *

 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package de.elatexam.httprobot;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Level;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;

import com.meterware.httpunit.Button;
import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.HttpUnitOptions;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebForm;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;
import com.meterware.httpunit.cookies.CookieProperties;


/**
 * Die Klasse Robot stellt einen HTML-Bot unter Verwendung HttpUnit zur
 * Verfügung.<br>
 * Die einzelnen Schritte werden dabei mit einer XML-Datei oder einem
 * entsprechenden XML-Root-Element (org.jdom.Element) übergeben.<br>
 * <br>
 * Im einzelnen sind vor allem folgende Methoden relevant:<br>
 * Robot.init ... Initialisierung, �bergabe Parameter und Protokolldateiname.<br>
 * Robot.run ... Abarbeitung der einzelnen Schritte, Übergabe XML-Ablaufplan<br>
 * <br>
 * Robot.getLastByteResult ... Rückgabe zuletzt übertragener Daten als byte[]<br>
 * Robot.getLastTextResult ... Rückgabe zuletzt übertragener Daten als String<br>
 * Robot.getLastInputStreamResult ... Rückgabe zuletzt übertragener Daten als
 * InputStream<br>
 * <br>
 * Die einzelnen Schritte müssen in der Klasse HTMLRobots implementiert sein.<br>
 * 
 * @see HTMLRobots
 * @author Oliver Niedtner
 * @version 0.9.1
 * 
 */
public class Robot {

	private WebConversation httpClient = null;
	private HTMLRobots htmlRobots = null;
	private Boolean ContinueIfError = true;

	private LinkedList<String> pending = null;

	private WebResponse lastWebResponse = null;
	private byte[] lastByteResult = null;
	private String lastTextResult = null;
	private String lastResultName = null;
	private int lastResultStatus = 0;

	
	//logging
	private static final org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(Robot.class);

	// Init, Run, Exit
	/**
	 * <br>
	 * - Standard-Konfiguration des Web-Clients setzen (httpUnit.WebConversation)<br>
	 * - Verifizierung aller ssl-Zertifikate (Klasse XTrustProvider)<br>
	 */
	Robot() {
		this.htmlRobots = new HTMLRobots();
		this.httpClient = new WebConversation();
		this.pending = new LinkedList<String>();

		this.lastByteResult = null;
		this.lastTextResult = null;
		this.lastResultName = null;
		this.lastResultStatus = 0;

		//Standard-Konfiguration des Web-Clients setzen
		this.httpClient.getClientProperties().setAutoRedirect(true);
		this.httpClient.getClientProperties().setAutoRefresh(true);
		this.httpClient.getClientProperties().setSendReferer(true);
		this.httpClient.getClientProperties().setAcceptGzip(true);
		this.httpClient.getClientProperties().setAcceptCookies(true);
		HttpUnitOptions.setScriptingEnabled(true);

		//Cookie Richtlinie
		CookieProperties.setDomainMatchingStrict(false);
		CookieProperties.setPathMatchingStrict(false);
		
		// Zertifikate einbinden
		XTrustProvider.install();

	} // Robot()

	
	/**
	 * Initialisierung den Bot:<br>
	 * - Speicherung �bergebener Parameter<br>
	 * - Initialisierung Logging<br>
	 * 
	 * @param protocolFileName
	 *            Dateiname der Protokolldatei
	 * @throws Exception
	 * @see XTrustProvider
	 */
	public void init(String protocolFileName) throws Exception {
		this.init(protocolFileName, null);
	} // init

	
	/**
	 * Initialisierung den Bot:<br>
	 * - Speicherung �bergebener Parameter<br>
	 * - Initialisierung Logging<br>
	 * 
	 * @param parameters
	 *            �bergabe Parameter, Format: NAME:WERT
	 * @throws Exception
	 */
	public void init(String[] parameters) throws Exception {
		this.init(null, parameters);
	} // init

	
	/**
	 * Initialisierung des Bots:<br>
	 * - Speicherung übergebener Parameter / Konfigurationseinstellungen<br>
	 * - Initialisierung Logging<br>
	 * 
	 * @param protocolFileName
	 *            Dateiname der Protokolldatei
	 * @param parameters
	 *            Übergabe Parameter, Format: NAME:WERT
	 * @throws Exception
	 */
	public void init(String protocolFileName, String[] parameters)
			throws Exception {
	  
		// Parameter
		this.pending = new LinkedList<String>();
		if ((parameters != null)) {
			for (String param : parameters) {
			  String[] params = param.split(":");
				if (!this.setHttpClientParameters(params[0], params[1])) {
					this.setPending("param", params[0], params[1]);
				} //if Parameter ist zur Konfiguration
			} // for
		} // if Parameter

		Robot.logger.info("LogLevel: " + Robot.logger.getLevel());
		Robot.logger.info("LogLevel: Zum �ndern den Start-Parameter ('LogLevel:ALL|TRACE|DEBUG|INFO|WARN|ERROR|FATAL') oder STEP-Element CONFIG verwenden.");



		// Ausgabe Infos httpClient
		this.printLogger(this.httpClient);
	} // init

	
	/**
	 * Ausff�hrung des XML-Ablaufplans.<br>
	 * 
	 * @param xmlFileName
	 *            Dateiname XML-Datei
	 * @throws Exception
	 */
	public void run(String xmlFileName) throws Exception {
		Document xmlFile = new SAXBuilder().build(xmlFileName);
		this.run(xmlFile.getRootElement());
	} // run

	/**
	 * Ausff�hrung des XML-Ablaufplans.<br>
	 * 
	 * @param eXMLRobotPlan
	 *            XML-Rootelement (org.jdom.Element)
	 * @throws Exception
	 */
	public void run(Element eXMLRobotPlan) throws Exception {
		List<Element> lSteps = eXMLRobotPlan.getChildren("step");
		for (Element eStep : lSteps) {
			this.callMethod("m" + eStep.getChildText("mode"), eStep);
		} // for
	} // run


	// getter/setter
	/**
	 * 
	 * @return aktuelles httpunit.WebResponse-Objekt
	 */
	WebResponse getLastWebResponse() {
		return this.lastWebResponse;
	} // getLastWebResponse

	
	/**
	 * 
	 * @return zuletzt �bertragene Daten als byte[]
	 */
	public byte[] getLastByteResult() {
		return this.lastByteResult;
	} // getLastByteResult

	
	/**
	 * 
	 * @return zuletzt �bertragene Daten als InputStream
	 */
	public InputStream getLastInputStreamResult() {
		return new ByteArrayInputStream(this.lastByteResult);
	}

	
	/**
	 * 
	 * @return zuletzt �bertragene Daten als String (null wenn Konvertierung aus
	 *         den �bertragenen Daten nicht m�glich)
	 */
	public String getLastTextResult() {
		return this.lastTextResult;
	} // getLastTextResult

	
	private void setLastByteResult(InputStream input) throws Exception {
		Robot.logger.debug("Methode: Robot.setLastByteResult");

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
    if ((input != null)) {
      int httpByte = 0;

      while ((httpByte = input.read()) != -1) {
        baos.write((byte) httpByte);
      } // while
    } // if dis
		this.lastByteResult = baos.toByteArray();
	} // setByteResult

	void setLastTextResult(String input) {
		Robot.logger.debug("Methode: Robot.setLastTextResult");
		Robot.logger.trace(input);
		this.lastTextResult = input;
	} // setLastTextResult

	
	/**
	 * gibt den Statuscode des letzten HTTP-Response
	 * 
	 * @return Statuscode des letzten HTTP-Response
	 */
	public int getLastResultStatus() {
		return this.lastResultStatus;
	} // getLastStatusResult
	
	
	/**
	 * gibt des Dateinamen des letzten HTTP-Response
	 * 
	 * @return Dateiname des letzten HTTP-Response
	 */
	public String getLastResultName() {
		if (this.lastResultName == null || this.lastResultName.equals("")) {
			return "result.$$$";
		} else if (this.lastResultName.equals(".html")) {
			return "result.html";
		} else {
			return this.lastResultName;
		} //if elsif else
	}

	
	/**
	 * speichert den letzten HTTP-Response als Datei<br>
	 * wenn m�glich im Unterverzeichnis "files" relativ zum aktuellen Arbeitsverzeichnis, sonst im aktuellen Arbeitsverzeichnis 
	 * 
	 * @return Dateiname inkl. absoluter Pfad der Datei
	 */
	public String saveLastResult () throws Exception{
		if (this.getLastByteResult().length > 0) {
			
			//speichern im Unterverzeichnis files
			File directory = new File("files");
			if (!directory.exists()) {
				//wenn Unterverzeichnis "files" nicht existent, anlegen
				try {
					directory.mkdir();
				} catch (Exception e) {
					directory = new File("");
				} //try catch
			} else if (!directory.isDirectory()) {
				directory = new File("");
			}
			return this.saveLastResult(directory.getAbsolutePath());
		} //if Datenspeicherung
		return null;
	}
	

	
	/**
	 * speichert den letzten HTTP-Response als Datei<br>
	 * - im angegebenen Verzeichnis 
	 * 
	 * @param path absolutes Verzeichnis
	 * 
	 * @return Dateiname inkl. absoluter Pfad der Datei
	 */
	public String saveLastResult (String path) throws Exception {
		File directory = new File(path);
		if (   (this.getLastByteResult().length > 0)
			&& directory.exists() 
			&& directory.isDirectory()
		   ) {
			//speichern im angegebenen Verzeichnis
			String filename = directory.getAbsolutePath() + File.separatorChar + this.getLastResultName();
			Robot.logger.info("Dateiausgabe: " + filename);
			FileOutputStream fos = new FileOutputStream(filename);
			fos.write(this.getLastByteResult());
			fos.flush();
			fos.close();
			return filename;
		} //if 
		return null;
	} //saveLastResult(String)

	
	
	
	
	/**
	 * Setzt Daten zur http-Authentifikation
	 * 
	 * @param realm
	 *            Umgebung
	 * @param username
	 *            Benutzername
	 * @param password
	 *            Password
	 * @throws Exception
	 */
	void setAuthentication(String realm, String username, String password)	throws Exception {
		this.httpClient.setAuthentication(realm, username, password);
	} // setAutehntication

	
	/**
	 * Gibt Wert zuvor gespeicherter Daten zur�ck.<br>
	 * Format: TYPE:NAME<br>
	 * Aufrufbeispiel Parameter: Robot.getPending("param:PARAMETERNAME")<br>
	 * 
	 * @param typeName
	 *            Type und Name der Daten, Format TYPE:NAME
	 * @return Wert
	 * @throws Exception
	 */
	String getPending(String typeName) throws Exception {
		if (typeName != null && typeName.contains(":")) {
			return this.getPending(typeName.split(":", 2)[0], typeName.split(":", 2)[1]);
		}
		return "";
	} // getPending

	
	/**
	 * Gibt Wert zuvor gespeicherter Daten zur�ck.<br>
	 * Aufrufbeispiel Parameter: Robot.getPending("param","PARAMETERNAME")<br>
	 * 
	 * 
	 * @param type
	 *            Typ der Daten
	 * @param name
	 *            Name der Daten
	 * @return Wert
	 * @throws Exception
	 */
	String getPending(String type, String name) throws Exception {
		if ((this.pending != null) && (this.pending.isEmpty() == false)) {
			for (String note : this.pending) {
				if (note.split("=")[0].equals(type + ":" + name)) {
					return note.split("=")[1];
				} // if
			} // for
		} // if
		return "";
	} // getPending

	
	/**
	 * Speichert Daten.
	 * 
	 * @param type
	 *            Typ der Daten
	 * @param name
	 *            Name der Daten
	 * @param value
	 *            Wert der Daten
	 * @throws Exception
	 */
	void setPending(String type, String name, String value) throws Exception {
		if ((type != null) && (name != null) && (value != null)
				&& (!type.equals("")) && (!name.equals(""))) {
			if (this.pending != null) {
				if (this.pending.isEmpty() == false) {
					for (int i = 0; i < this.pending.size() - 1; i++) {
						if (this.pending.get(i).split("=")[0] == (type + ":" + name)) {
							this.pending.set(i, type + ":" + name + "=" + value);
							return;
						} // if
					} // for
				} // if empty
				this.pending.add(type + ":" + name + "=" + value);
			} // if this.pending != null
		} // if
	} // setPending

	
	/**
	 * Setzt Konfigurationseinstellungen des Bots.<br>
	 * <br>
	 * Konfigurations�bersicht / Standardeinstellungen:<br>
	 * <ul>
	 * <li>AutoRedirect: true [true | false]</li>
	 * <li>AutoRefresh: true [true | false]</li>
	 * <li>SendReferer: true [true | false]</li>
	 * <li>AcceptGzip: true [true | false]</li>
	 * <li>AcceptCookies: true [true | false]</li>
 	 * <li>ExecJavaScript: true [true | false]</li>
 	 * <li>ContinueIfError: true [true | false]</li>
 	 * <li>UserAgent: httpunit/1.5 [beliebige Zeichenkette]</li>
  	 * <li>LogLevel: WARN [ALL | TRACE | DEBUG | INFO | WARN | ERROR | FATAL | OFF]</li>
	 * </ul>
	 * 
	 * @param name Parameterbezeichnung
	 * @param sValue Parameterwert
	 * 
	 * @return Konfigurationsparameter gefunden und gesetzt 
	 */
	boolean setHttpClientParameters (String name, String sValue) {
		if ((name != null) && (name != "")) {
			//Unterscheidung der Verarbeitung: Value als String oder Boolean
			//1. Verarbeitung als String
		  Robot.logger.setLevel(Level.WARN);
			if (name.equals("LogLevel")) {
				// ALL | TRACE | DEBUG | INFO | WARN | ERROR | FATAL | OFF
				Robot.logger.setLevel(Level.toLevel(sValue.toUpperCase(), Level.WARN));
				return true;
			} else if (name.equals("UserAgent")) {
					this.httpClient.getClientProperties().setUserAgent(sValue);
					return true;
			} else { //2. Verarbeitung als Boolean
				boolean bValue = Boolean.parseBoolean(sValue);
				if (name.equals("AutoRedirect")) {
					this.httpClient.getClientProperties().setAutoRedirect(bValue);
					return true;
				} else if (name.equals("AutoRefresh")) {
					this.httpClient.getClientProperties().setAutoRefresh(bValue);
					return true;
				} else if (name.equals("SendReferer")) {
					this.httpClient.getClientProperties().setSendReferer(bValue);
					return true;
				} else if (name.equals("AcceptGzip")) {
					this.httpClient.getClientProperties().setAcceptGzip(bValue);
					return true;
				} else if (name.equals("AcceptCookies")) {
					this.httpClient.getClientProperties().setAcceptCookies(bValue);
					return true;
				} else if (name.equals("ExecJavaScript")) {
					HttpUnitOptions.setScriptingEnabled(bValue);
					return true;
				} else if (name.equals("ContinueIfError")) {
					this.ContinueIfError = bValue;
					return true;
				} //if -> versch. Boolean-Konfigurationen
				
			} //if ->Unterscheidung der Verarbeitung
				
			
		} //if name->ok
		return false;
	} //setHttpClientParameters
	


	
// Hilfsfunktionen

	// Logging

	/**
	 * Loggt alle relevanten Daten eines Formulars (httpunit.WebForm)
	 * 
	 * @param form
	 */
	void printLogger(WebForm form) {
		// Logging Form: ohne Parameter
		Robot.logger.info("Form: " + form.getName());
		Robot.logger.trace("   Methode: " + form.getMethod());
		Robot.logger.trace("   Action: " + form.getAction());

		Robot.logger.trace("   Parameter: [Name] --- [Value]");
		for (String s : form.getParameterNames()) {
			Robot.logger.trace("   Parameter: " + s + " --- " + form.getParameterValue(s));
		}

		Robot.logger.trace("   Button: [Name] --- [ID] --- [Type] --- [Value]");
		for (Button b : form.getButtons()) {
			Robot.logger.trace("   Button: " + b.getName() + " --- " + b.getID() + " --- " + b.getType() + " --- " + b.getValue());
		}

		// robot.logger.info("Action: " + form);
	} // printLogger WebForm


	
	/**
	 * Loggt alle relevanten Daten des aktuellen Web-Clients (httpunit.WebConversation)<br>
	 * zzgl. LogLevel und Pending
	 */
	void printLoggerWebClient () {
		//Logging Web-Client
		this.printLogger(this.httpClient);
		
		//Logging LogLevel
		Robot.logger.info("   LogLevel: " + Robot.logger.getLevel());
		
		//Logging Pending
		if (Robot.logger.isTraceEnabled() && (this.pending != null) && (this.pending.isEmpty() == false)) {
			Robot.logger.trace("Pending:");
			for (String note : this.pending) {
				Robot.logger.trace(note);
			} // for
		} // if
	} //printLoggerWebClient
	
	
	/**
	 * Loggt alle relevanten Daten eines Web-Clients (httpunit.WebConversation)
	 * 
	 * @param client
	 */
	void printLogger (WebConversation client) {
		Robot.logger.info("Client: [Name] --- [Value]");
		Robot.logger.info("   AutoRedirect: " + client.getClientProperties().isAutoRedirect());
		Robot.logger.info("   AutoRefresh: " + client.getClientProperties().isAutoRefresh());
		Robot.logger.info("   SendReferer: " + client.getClientProperties().isSendReferer());
		Robot.logger.info("   UserAgent: " + client.getClientProperties().getUserAgent());
		Robot.logger.info("   AcceptGZip: " + client.getClientProperties().isAcceptGzip());
		Robot.logger.info("   AcceptCokies: " + client.getClientProperties().isAcceptCookies());
		Robot.logger.info("   ExecJavaScript: " + HttpUnitOptions.isScriptingEnabled());
	} // printLogger WebConversation

	/**
	 * Loggt alle relevanten Daten eines Web-Response (httpunit.WebResponse)
	 * 
	 * @param response
	 */
	void printLogger(WebResponse response) {
		Robot.logger.info("Response: " + response.getURL().toString());
		Robot.logger.debug("   Status: " + response.getResponseCode() + " (" + response.getResponseMessage() + ")");

		if (Robot.logger.isDebugEnabled()) {
			Robot.logger.debug("   Cookie: [Name] --- [Value]");
			for (String s : response.getClient().getCookieNames()) {
				Robot.logger.debug("   Cookie  : " + s + " --- " + response.getClient().getCookieValue(s));
				Robot.logger.trace("      URL  : " + response.getClient().getCookieDetails(s).getDomain());
				Robot.logger.trace("      End  : " + response.getClient().getCookieDetails(s).getExpiredTime());
				Robot.logger.trace("      Path : " + response.getClient().getCookieDetails(s).getPath());
				Robot.logger.trace("      Name : " + response.getClient().getCookieDetails(s).getName());
				Robot.logger.trace("      Value: " + response.getClient().getCookieDetails(s).getValue());
			} //for Cookie
			
			Robot.logger.debug("   CookieNew: [Name] --- [Value]");
			for (String s : response.getNewCookieNames()) {
				Robot.logger.debug("   CookieNew: " + s + " --- " + response.getNewCookieValue(s));
			} // for CookieNew
			
			Robot.logger.debug("   Header: [Name] --- [Value]");
			for (String s : response.getHeaderFieldNames()) {
				Robot.logger.debug("   Header: " + s + " --- " + response.getHeaderField(s));
			} //for Header
			
		} // if DebugEnabled
	} // printLogger WebResponse

	
	
	
	
	
	
	// HTMLRobots-call-Funktionen
	private String callMethod(String methode, Element eStep) throws Exception {
		Robot.logger.info("Methode: Robot.callMethode -- Aufruf " + methode);
		try {
			return this.htmlRobots.getClass().getMethod(methode,
					this.getClass(), eStep.getClass()).invoke(this.htmlRobots,
					this, eStep).toString();
		} catch (Exception e) {
			if (Robot.logger.isDebugEnabled()) {
				Robot.logger.debug("FEHLER Robot.callElement: " + methode, e);
			} else {
				Robot.logger.warn("FEHLER Robot.callStep: " + methode);
				Robot.logger.warn(e.toString());
			} // if else
			if (!this.ContinueIfError) {
				new Exception("Fehler in der Abarbeitung, ContinueIfError==false, Fehlermeldung siehe Logdatei.\nDatum / Uhrzeit: " 
						        + new SimpleDateFormat("yyyyMMdd-HHmm").format(new Date()));
			}
		} // try catch
		return "";
	} // callMethod

	private String callMethod(String methode, String htmlCode, String name) {
		try {
			return this.htmlRobots.getClass().getMethod(methode,
					this.getClass(), htmlCode.getClass(), name.getClass())
					.invoke(this.htmlRobots, this, htmlCode, name).toString();
		} catch (Exception e) {
			if (Robot.logger.isDebugEnabled()) {
				Robot.logger.debug("FEHLER Robot.callElement: " + methode, e);
			} else {
				Robot.logger.warn("FEHLER Robot.callStep: " + methode);
				Robot.logger.warn(e.toString());
			} // if else
			if (!this.ContinueIfError) {
				new Exception("Fehler in der Abarbeitung, ContinueIfError==false, Fehlermeldung siehe Logdatei.\nDatum / Uhrzeit: " 
						        + new SimpleDateFormat("yyyyMMdd-HHmm").format(new Date()));
			}
		} // try catch
		return "";
	} // call Method

	
	
	
	/**
	 * F�hrt einen Web-Request mit dem aktuellen Web-Client aus.
	 * 
	 * @param request
	 *            auszuf�hrender Web-Request (httpunit.WebRequest)
	 * @return Erfolg der Ausf�hrung
	 * @throws Exception
	 */
	Boolean execHTTP(WebRequest request) throws Exception {
		Robot.logger.debug("execHTTP: " + request.getURL().toString());
		this.lastWebResponse = this.httpClient.getResource(request);

		if (this.lastWebResponse != null) {
			this.printLogger(this.lastWebResponse);

			// for (String s :
			// this.lastWebResponse.getHeaderFields("set-cookie")) {
			// this.httpClient.putCookie(s.split("=", 2)[0], s.split("=",
			// 2)[1]);
			// }

			for (String s : this.lastWebResponse.getNewCookieNames()) {
				this.httpClient.putCookie(s, this.lastWebResponse
						.getNewCookieValue(s));
			} // for

			switch (this.lastResultStatus = this.lastWebResponse.getResponseCode()) {
			case 301:
			case 302:
			case 303:
			case 304:
			case 305:
			case 306:
			case 307: // http-Codes 301-307: Redirect, wenn AutoRedirect == true
				if (this.httpClient.getClientProperties().isAutoRedirect()) {
					Robot.logger.debug("REDIRECT " + this.lastResultStatus + ": " + this.lastWebResponse.getHeaderField("location"));
					return this.execHTTP(new GetMethodWebRequest(new URL(this.lastWebResponse.getURL(), this.lastWebResponse.getHeaderField("location")).toString()));
				} // if
				// case 301-307: Redirect
			default:
				//Refresh per Meta-Tag wenn AutoRefresh == true
				if (    this.httpClient.getClientProperties().isAutoRefresh()
					&&  this.lastWebResponse.isHTML()
					&& (this.lastWebResponse.getMetaTagContent("http-equiv", "refresh") != null) 
					&& (this.lastWebResponse.getMetaTagContent("http-equiv", "refresh").length > 0)
				   ) {
					String refresh = this.lastWebResponse.getMetaTagContent("http-equiv", "refresh")[0];
					Robot.logger.debug("REFRESH: " + refresh);
					int refresh_timeout = Integer.parseInt(refresh.split(";", 2)[0]);
					String refresh_url  = refresh.split(";", 2)[1].replaceFirst("url=", "");
					Thread.sleep(refresh_timeout*1000+100);
					return this.execHTTP(new GetMethodWebRequest(new URL(this.lastWebResponse.getURL(), refresh_url).toString()));
				} //if Refresh per Meta-Tag
				
				
				//speichern korrekter Dateiname (entweder aus Header oder aus URL)
				if (   (this.lastWebResponse.getHeaderField("CONTENT-DISPOSITION") != null)
					&&  !this.lastWebResponse.getHeaderField("CONTENT-DISPOSITION").equals("")
				   ) {
					this.lastResultName = this.lastWebResponse.getHeaderField("CONTENT-DISPOSITION").replaceAll(";", "").replaceAll(".*?filename=", "").replaceAll("\"", "");
				} else {
					this.lastResultName = this.lastWebResponse.getURL().getPath().replaceAll("/.*/", "");
					if (   this.lastWebResponse.isHTML() 
						&& !(this.lastResultName.endsWith(".htm") || this.lastResultName.endsWith(".html")) 
					   ) {
						this.lastResultName += ".html";
					} //if
				} //if else Dateiname im Header �bertragen
				
				
				//speichern Inhalt
				this.setLastByteResult(this.lastWebResponse.getInputStream());
				if (this.lastWebResponse.isHTML()) {
					this.setLastTextResult(this.lastWebResponse.getText());
				} // if
				
				return true;
				// default
			} // switch
		} // if
		return false;
	} // execHTTP

	
	

	
	
	
	
	
	
	
	// Main
	/**
	 * Hauptmethode<br>
	 * <ul>
	 * <li>1. Paramter: XML-Datei</li>
	 * <li>ab 2. Paramter:<br>
	 *     - Konfiguration (siehe setHttpClientParameters) oder<br> 
	 *     - Paramter f�r die Abarbeitung (siehe Klasse HTMLRobots)</li>
	 * </ul>
	 * @see setHttpClientParameters
	 * @see HTMLRobots
	 * @param args Parameter
	 * @throws Exception
	 */
	public static void main (String[] args) throws Exception {

		if ((args != null) && (args.length > 0)) {
			Robot robot;
			robot = new Robot();
			
			//1. Parameter abspalten, da XML-Datei
			if (args.length > 1) {
			  String[] params = new String[args.length-1];
			  System.arraycopy(args, 1, params, 0, params.length);
			  
				robot.init(args[0], params);
			} else {
				robot.init(args[0]);
			} // if else
			robot.run(args[0]);
			System.out.println(new String(robot.getLastByteResult()));
			robot.saveLastResult();
		} else {
			Robot.logger.fatal("Parameter fehlt!");
			System.out.println("Parameter fehlt!");
			System.out.println("java Robot XMLDateiname Parameter");
		} // if else
	} // main


} // class Robot


