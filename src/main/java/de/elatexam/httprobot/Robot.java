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
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Level;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
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

  private final Map<String, Map<String, String>> pending;

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
   *
   * @throws Exception
   */
  public Robot(final String[] parameters) {
		this.htmlRobots = new HTMLRobots();
		this.httpClient = new WebConversation();
    this.pending = new HashMap<String, Map<String, String>>();

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

    init(parameters);
	} // Robot()


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
  public void init(final String[] parameters) {

		// Parameter
		if ((parameters != null)) {
			for (final String param : parameters) {
			  final String[] params = param.split(":");
        if (params.length != 2) {
          throw new IllegalArgumentException("Invalid parameter syntax, expected: 'name:value', but was: " + param);
        }
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
   * Run robotPlan as specified in XML provided by an inputstream.
   * 
   * @param in
   *          inputstream containing a robotplan xml
   */
  public void run(final InputStream in) {
    Document xmlFile;
    try {
      xmlFile = new SAXBuilder().build(in);
      this.run(xmlFile.getRootElement());
    } catch (final JDOMException e) {
      throw new RuntimeException(e);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }

  }
	/**
	 * Ausff�hrung des XML-Ablaufplans.<br>
	 *
	 * @param xmlFileName
	 *            Dateiname XML-Datei
	 * @throws Exception
	 */
	public void run(final String xmlFileName) throws Exception {
		final Document xmlFile = new SAXBuilder().build(xmlFileName);
		this.run(xmlFile.getRootElement());
	} // run

	/**
	 * Ausff�hrung des XML-Ablaufplans.<br>
	 *
	 * @param eXMLRobotPlan
	 *            XML-Rootelement (org.jdom.Element)
	 * @throws Exception
	 */
  public void run(final Element eXMLRobotPlan) {
		final List<Element> lSteps = eXMLRobotPlan.getChildren("step");
		for (final Element eStep : lSteps) {
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


	private void setLastByteResult(final InputStream input) throws Exception {
		Robot.logger.debug("Methode: Robot.setLastByteResult");

		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    if ((input != null)) {
      int httpByte = 0;

      while ((httpByte = input.read()) != -1) {
        baos.write((byte) httpByte);
      } // while
    } // if dis
		this.lastByteResult = baos.toByteArray();
	} // setByteResult

	void setLastTextResult(final String input) {
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
				} catch (final Exception e) {
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
	public String saveLastResult (final String path) throws Exception {
		final File directory = new File(path);
		if (   (this.getLastByteResult().length > 0)
			&& directory.exists()
			&& directory.isDirectory()
		   ) {
			//speichern im angegebenen Verzeichnis
			final String filename = directory.getAbsolutePath() + File.separatorChar + this.getLastResultName();
			Robot.logger.info("Dateiausgabe: " + filename);
			final FileOutputStream fos = new FileOutputStream(filename);
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
	void setAuthentication(final String realm, final String username, final String password)	throws Exception {
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
	String getPending(final String typeName) throws Exception {
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
   *          Typ der Daten
   * @param name
   *          Name der Daten
   * @return Wert
   * @throws Exception
   */
  String getPending(final String type, final String name) throws Exception {
    final Map<String, String> valuesOfType = this.pending.get(type);
    String result = "";
    if (valuesOfType != null) {
      result = valuesOfType.get(name);
    }
    return result;
  }


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
  void setPending(final String type, final String name, final String value) {
    Map<String, String> map = pending.get(type);
    if (map == null) {
      map = new HashMap<String, String>();
      pending.put(type, map);
    }
    map.put(name, value);
  }


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
	boolean setHttpClientParameters (final String name, final String sValue) {
		if ((name != null) && (name != "")) {
			//Unterscheidung der Verarbeitung: Value als String oder Boolean
			//1. Verarbeitung als String
			if (name.equals("LogLevel")) {
				// ALL | TRACE | DEBUG | INFO | WARN | ERROR | FATAL | OFF
				Robot.logger.setLevel(Level.toLevel(sValue.toUpperCase(), Level.WARN));
				return true;
			} else if (name.equals("UserAgent")) {
					this.httpClient.getClientProperties().setUserAgent(sValue);
					return true;
			} else { //2. Verarbeitung als Boolean
				final boolean bValue = Boolean.parseBoolean(sValue);
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
	void printLogger(final WebForm form) {
		// Logging Form: ohne Parameter
		Robot.logger.info("Form: " + form.getName());
		Robot.logger.trace("   Methode: " + form.getMethod());
		Robot.logger.trace("   Action: " + form.getAction());

		Robot.logger.trace("   Parameter: [Name] --- [Value]");
		for (final String s : form.getParameterNames()) {
			Robot.logger.trace("   Parameter: " + s + " --- " + form.getParameterValue(s));
		}

		Robot.logger.trace("   Button: [Name] --- [ID] --- [Type] --- [Value]");
		for (final Button b : form.getButtons()) {
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
    if (Robot.logger.isTraceEnabled() && (this.pending.isEmpty() == false)) {
      Robot.logger.trace("Pending: ");
      Robot.logger.trace(pending);
		} // if
	} //printLoggerWebClient


	/**
	 * Loggt alle relevanten Daten eines Web-Clients (httpunit.WebConversation)
	 *
	 * @param client
	 */
	void printLogger (final WebConversation client) {
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
	void printLogger(final WebResponse response) {
		Robot.logger.info("Response: " + response.getURL().toString());
		Robot.logger.debug("   Status: " + response.getResponseCode() + " (" + response.getResponseMessage() + ")");

		if (Robot.logger.isDebugEnabled()) {
			Robot.logger.debug("   Cookie: [Name] --- [Value]");
			for (final String s : response.getClient().getCookieNames()) {
				Robot.logger.debug("   Cookie  : " + s + " --- " + response.getClient().getCookieValue(s));
				Robot.logger.trace("      URL  : " + response.getClient().getCookieDetails(s).getDomain());
				Robot.logger.trace("      End  : " + response.getClient().getCookieDetails(s).getExpiredTime());
				Robot.logger.trace("      Path : " + response.getClient().getCookieDetails(s).getPath());
				Robot.logger.trace("      Name : " + response.getClient().getCookieDetails(s).getName());
				Robot.logger.trace("      Value: " + response.getClient().getCookieDetails(s).getValue());
			} //for Cookie

			Robot.logger.debug("   CookieNew: [Name] --- [Value]");
			for (final String s : response.getNewCookieNames()) {
				Robot.logger.debug("   CookieNew: " + s + " --- " + response.getNewCookieValue(s));
			} // for CookieNew

			Robot.logger.debug("   Header: [Name] --- [Value]");
			for (final String s : response.getHeaderFieldNames()) {
				Robot.logger.debug("   Header: " + s + " --- " + response.getHeaderField(s));
			} //for Header

		} // if DebugEnabled
	} // printLogger WebResponse







	// HTMLRobots-call-Funktionen
  private String callMethod(final String methode, final Element eStep) {
		Robot.logger.info("Methode: Robot.callMethode -- Aufruf " + methode);
		try {
			return this.htmlRobots.getClass().getMethod(methode,
					this.getClass(), eStep.getClass()).invoke(this.htmlRobots,
					this, eStep).toString();
		} catch (final Exception e) {
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

	private String callMethod(final String methode, final String htmlCode, final String name) {
		try {
			return this.htmlRobots.getClass().getMethod(methode,
					this.getClass(), htmlCode.getClass(), name.getClass())
					.invoke(this.htmlRobots, this, htmlCode, name).toString();
		} catch (final Exception e) {
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
	Boolean execHTTP(final WebRequest request) throws Exception {
		Robot.logger.debug("execHTTP: " + request.getURL().toString());
		this.lastWebResponse = this.httpClient.getResource(request);

		if (this.lastWebResponse != null) {
			this.printLogger(this.lastWebResponse);

			// for (String s :
			// this.lastWebResponse.getHeaderFields("set-cookie")) {
			// this.httpClient.putCookie(s.split("=", 2)[0], s.split("=",
			// 2)[1]);
			// }

			for (final String s : this.lastWebResponse.getNewCookieNames()) {
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
					final String refresh = this.lastWebResponse.getMetaTagContent("http-equiv", "refresh")[0];
					Robot.logger.debug("REFRESH: " + refresh);
					final int refresh_timeout = Integer.parseInt(refresh.split(";", 2)[0]);
					final String refresh_url  = refresh.split(";", 2)[1].replaceFirst("url=", "");
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
	public static void main (final String[] args) throws Exception {

		if ((args != null) && (args.length > 0)) {
			Robot robot;

			//1. Parameter abspalten, da XML-Datei
			if (args.length > 1) {
			  final String[] params = new String[args.length-1];
			  System.arraycopy(args, 1, params, 0, params.length);

        robot = new Robot(params);
			} else {
        robot = new Robot(null);
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


