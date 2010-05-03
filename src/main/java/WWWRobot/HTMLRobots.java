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

package WWWRobot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.lang.reflect.*;

import org.jdom.Element;

import WWWRobot.Robot;

import com.meterware.httpunit.*;

/**
 * Die Klasse HTMLRobots beinhaltet die einzelnen, speziellen
 * Abarbeitungsanweisungen für die entsprechenden XML-Dateien.<br>
 * Der eigentliche Bot ist in der Klasse Robot realisiert. Grundstruktur der
 * XML-Dateien:<br>
 * <br> 
 * {@code <robotPlan> }<br> 
 * {@code ��<step> }<br> 
 * {@code ����<mode> }<br> 
 * {@code ��</step> }<br> 
 * {@code ��<step> }<br> 
 * {@code ����<mode> }<br> 
 * {@code ��</step> }<br> 
 * {@code </robotPlan> }<br>
 * <br>
 * Wobei {@code <step>} die einzelnen Abarbeitungsschritte repräsentieren.<br>
 * <br>
 * Parameter können auch bei Aufruf Robot.main direk via java oder bei Aufruf
 * Robot.init als String[] übergeben werden.<br>
 * Format: PARAMETERNAME:PARAMETERWERT<br>
 * Ein Verweis in der XML-Datei ist via param:PARAMETERNAME möglich.<br>
 * <br>
 * Die Methoden werden entpsrechend der im Element {@code <mode>} festgelegten
 * Bezeichung aufgerufen, wobei dem angegebenen Namen ein "m" vorangetstellt
 * wird.<br>
 * Beispiel: {@code "<mode>GET</mode>"} --> Methodenaufruf: "mGET" <br>
 * 
 * @see Robot
 * @author Oliver Niedtner
 * @version 0.9.1
 */
public class HTMLRobots {
	
	//Konfigurations-Funktion
	/**Setzt bzw. �ndert die Konfigurationseinstellungen des Client.<br>
	 * <br>
	 * Aufbau:
	 * {@code <mode> } - CONFIG<br> 
	 * {@code <param> } - [ein zu setzender Parameter; XML-Bereich kann auch mehrfach vorkommen, entsprechend je Parameter]<br> 
	 * {@code ��<name> } - [Konfigurations- / Parameterbezeichnung]<br>
	 * {@code ��<value> } - [Konfigurations- / Parameterwert]: true / false (Ausnahme: LogLevel s.u.)<br> 
	 * {@code </param> }<br>
	 * Alle nicht unten aufgef�hrten Konfigurationseinstellungen werden als Parameter f�r die Abarbeitung genutzt und entsprechend gespeichert.<br>
	 * Zugriff in der XML-Datei via "param:[Parameterbezeichnung] m�glich.<br>  
	 * <br>
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
 	 * <li>UserAgent: httpunit/1.5 [String]</li>
  	 * <li>LogLevel: WARN [ALL | TRACE | DEBUG | INFO | WARN | ERROR | FATAL | OFF]</li>
	 * </ul>
	 * 
	 * 
	 * @param robot
	 *            Objektinstanz des eigentlichen Robot
	 * @param mStep
	 *            JDOM-XML-Element des Abarbeitungsschrittes
	 * @return erfolgreiche Abarbeitung (true/false)
	 * 
	 */
	public Boolean mCONFIG (Robot robot, Element mStep) throws Exception {
		List<Element> parameters = mStep.getChildren("param");
		for (Element param : parameters) {
			if ( (param.getChildText("name") != null) && !param.getChildText("name").equals("")
				 && (param.getChildText("value") != null) && !param.getChildText("value").equals("")
			   ) {
					if (!robot.setHttpClientParameters(param.getChildText("name"), param.getChildText("value"))) {
						robot.setPending("param", param.getChildText("name"), param.getChildText("value"));
					}
			} //if
		} //for
		robot.printLoggerWebClient();
		return true;
	}

	
	
	// HTML-Funktionen
	/**
	 * Ruft den Inhalt via http ab.<br>
	 * <br>
	 * Verwendete HTML-Parameter:<br> 
	 * {@code <mode> } - GET<br> 
	 * {@code <url> } - [URL des Auffrufs]<br>
	 * <br>
	 * 
	 * @param robot
	 *            Objektinstanz des eigentlichen Robot
	 * @param mStep
	 *            JDOM-XML-Element des Abarbeitungsschrittes
	 * @return erfolgreiche Abarbeitung (true/false)
	 * 
	 */
	public Boolean mGET(Robot robot, Element mStep) throws Exception {
		//http-Request
		return (   (mStep.getChildText("url") != null) 
			    && robot.execHTTP(new GetMethodWebRequest(mStep.getChildText("url")))
			   );
	} // mGET

	
	
	/**
	 * Ruft den Inhalt eines Links via http ab und simuliert somit ein Klick auf
	 * diesen. <br>
	 * Der Link wird in der letzten �bermittelten http-Antwort gesucht.<br>
	 * <br>
	 * Verwendete XML-Parameter:<br> 
	 * {@code <mode> } - GET_Link<br> 
	 * {@code <name> } - [Linkbezeichnung]<br>
	 * <br>
	 * Suchreihenfolge:
	 * <ul>
	 * <li>Linktext</li>
	 * <li>Tag: name</li>
	 * <li>Tag: id</li>
	 * <li>Linkbild, Tag: alt</li>
	 * </ul>
	 * 
	 * @param robot
	 *            Objektinstanz des eigentlichen Robot
	 * @param mStep
	 *            JDOM-XML-Element des Abarbeitungsschrittes
	 * @return erfolgreiche Abarbeitung (true/false)
	 * 
	 */
	public Boolean mGET_Link(Robot robot, Element mStep) throws Exception {
		WebResponse r;
		//Abfrage des Links nach versch. Kriterien
		String linkName = mStep.getChildText("name");
    if (!robot.getPending(linkName).equals("")) {
      linkName = robot.getPending(linkName);
    }
    WebLink link = robot.getLastWebResponse().getLinkWith(linkName);
		if (link == null) link = robot.getLastWebResponse().getLinkWithName(linkName);
		if (link == null) link = robot.getLastWebResponse().getLinkWithID(linkName);
		if (link == null) link = robot.getLastWebResponse().getLinkWithImageText(linkName);
		//http-Request
		return ((link != null) && robot.execHTTP(link.getRequest()));
	} // mGET_Link


	
	/**
	 * Speichert den letzten http-Response als Datei.<br>
	 * <br>
	 * Verwendete HTML-Parameter:<br> 
	 * {@code <mode> } - SAVE<br> 
	 * {@code <path> } - Speicherverzeichnis, wenn leer dann wird Unterverzeichnis "files" verwendet<br>
	 * {@code <saveID> } - ID der Datei (zur Identifizierung des Abarbeitungsparamters)<br>
	 * <br>
	 * Gespeicherte Daten werden mit Dateinamen als Abarbeitungsparameter gespeichert:<br>
	 * - Typ: save<br>
	 * - Name: {@code <saveID> }<br>
	 * - Value: Dateiname<br>
	 * - siehe Robot.getPending<br>
	 * 
	 * @param robot
	 *            Objektinstanz des eigentlichen Robot
	 * @param mStep
	 *            JDOM-XML-Element des Abarbeitungsschrittes
	 * @return erfolgreiche Abarbeitung (true/false)
	 * 
	 */
	public Boolean mSAVE(Robot robot, Element mStep) throws Exception {

		String FileName = null;
		//Verzeichnis angegeben?
		if ((mStep.getChildText("path") != null) && !mStep.getChildText("path").equals("")) {
			FileName = robot.saveLastResult(mStep.getChildText("path"));
		} else {
			FileName = robot.saveLastResult();
		} //if else Verzeichnis angegeben?
		
		//im Pending speichern
		robot.setPending(  "save" 
				       	 , (mStep.getChildText("saveID") != null) ? mStep.getChildText("saveID") : "null" 
				       	 , FileName);
		return true;
	} // mSAVE

	
	
	
	
	/**
	 * Belegt in einem Formular die Werte und sendet es an den Webserver.
	 * Simuliert somit ein Ausf�llen und Abschicken des Formulares.<br>
	 * Das Formular wird in der letzten �bermittelten
	 * http-Antwort gesucht, es wird das entsprechende vorgegebene �bermittlungs-Verfahren (POST/GET) verwendet.<br>
	 * <br>
	 * Verwendete XML-Parameter:<br> 
	 * {@code <mode> } - POST<br> 
	 * {@code <name> } - [Formularbezeichnung]<br> 
	 * {@code <id> } - [Formularidentifikationsnummer]<br> 
	 * {@code <button> } - [Buttonname der zum �bermitteln des Formulares benutzt wird]<br> 
	 * {@code <param> } - [ein mit Daten zu belegender Parameter; XML-Bereich kann auch mehrfach vorkommen, entsprechend je Parameter]<br> 
	 * {@code ��<type> } - [Parameter-Typ entsprechend HTML-Syntax]<br> 
	 * {@code ��<name> } - [Parameterbezeichnung]<br>
	 * {@code ��<value> } - [Parameterwert]<br> 
	 * {@code ��<state> } - [Parameterstatus (nur bei Typ 'checkbox' - checked = true, unchecked = false]<br> 
	 * {@code </param> }<br>
	 * <br>
	 * Suchreihenfolge Formular:
	 * <ul>
	 * <li>Tag: name (XML-Paramter {@code <name>})</li>
	 * <li>Tag: id (XML-Paramter {@code <name>})</li>
	 * <li>Identifikationsnummer (XML-Paramter {@code <id>})</li>
	 * <li>im Formular vorhander Button (XML-Paramter {@code <button>})</li>
	 * </ul>
	 * Suchreihenfolge Button:
	 * <ul>
	 * <li>Tag: name (XML-Paramter {@code <name>})</li>
	 * <li>Tag: id (XML-Paramter {@code <name>})</li>
	 * <li>Tag: value (XML-Paramter {@code <name>})</li>
	 * <li>Standard-Request</li>
	 * </ul>
	 * <br>
	 * Folgende Parameter-Typen werden unterst�tzt:<br>
	 *  - checkbox<br>
	 *  - multiple<br>
	 *  - alle weiteren, die eine einfach Zuordnung zw. Parameterbezeichnung unf Parameterwert �bertragen<br>
	 *  &nbsp;&nbsp; z.B.: hidden, input, text, select (vgl. HTML-Syntax)<br>
	 *  <br>
	 * 
	 * @param robot
	 *            Objektinstanz des eigentlichen Robot
	 * @param mStep
	 *            JDOM-XML-Element des Abarbeitungsschrittes
	 * @return erfolgreiche Abarbeitung (true/false)
	 * 
	 */
	public Boolean mPOST(Robot robot, Element mStep) throws Exception {
		WebForm form = null;
		SubmitButton sb = null;
		if ((mStep.getChildText("name") != null) && !mStep.getChildText("name").equals("")) {
			form = robot.getLastWebResponse().getFormWithName(mStep.getChildText("name"));
			if (form == null) {
				form = robot.getLastWebResponse().getFormWithID(mStep.getChildText("name"));
			} //if - Abfrage via ID, wenn Tag "name=" im Quelltext nicht verwendet.
		} //if mStep.getChildText("name")->ok
		if (form == null) {
			//wenn Spezifikation via Tag name/id nicht erfolgreich, dann Spezifikation via ID-Nummer oder Button-value
			if ((mStep.getChildText("id") != null) && !mStep.getChildText("id").equals("")) {
				form = robot.getLastWebResponse().getForms()[Integer.parseInt(mStep.getChildText("id"))];
			} else {
				for (WebForm form_elem : robot.getLastWebResponse().getForms()) {
					if ((sb = this.getSubmitButton(form_elem, mStep.getChildText("button"))) != null) {
						form = form_elem;
						break;
					} //if
				} //for
			} //if else
		} //if form==null
		if (form != null) {
			robot.printLogger(form);
			List<Element> parameters = mStep.getChildren("param");
			for (Element param : parameters) {
				Boolean state = Boolean.valueOf(param.getChildText("state"));
				// checkbox
				if (param.getChildText("type").equalsIgnoreCase("checkbox")) {
					if (state) {
						form.setCheckbox(param.getChildText("name"), param
								.getChildText("value"), state);
					} else {
						form.removeParameter(param.getChildText("name"));
					}
					// multiple
				} else if (param.getChildText("type").equals("multiple")) {
					LinkedList<String> s = new LinkedList<String>(Arrays
							.asList(form.getParameterValues(param
									.getChildText("name"))));
					if (state && !s.contains(param.getChildText("value"))) {
						s.add(param.getChildText("value"));
					} else if (!state
							&& !s.contains(param.getChildText("value"))) {
						s.remove(param.getChildText("value"));
					} // if elseif
					form.setParameter(param.getChildText("name"), (String[]) s
							.toArray());
					// sonst
				} else {
					String value = param.getChildText("value");
					if (!robot.getPending(value).equals("")) {
						value = robot.getPending(value);
					}
					form.setParameter(param.getChildText("name"), value);
				} // if else if else
			} // for parameters
			robot.printLogger(form);
			if (sb == null) sb = this.getSubmitButton(form, mStep.getChildText("button"));
			return  robot.execHTTP(sb != null ? form.getRequest(sb) : form.getRequest()); //wenn sb==null, dann Standard-Button ausf�hren
		} // if form != null
		return false;
	} // mPOST

	
	
	/**
	 * Legt eine http-Authentifizierung fest.<br>
	 * <br>
	 * Verwendete HTML-Parameter:<br> 
	 * {@code <mode> } - AUTH<br> 
	 * {@code <realm> } - [Bereich der Authentifizierung]<br> 
	 * {@code <username>} - [Benutzername]<br>
	 * {@code <password>} - [Passwort]<br>
	 * <br>
	 * 
	 * @param robot
	 *            Objektinstanz des eigentlichen Robot
	 * @param mStep
	 *            JDOM-XML-Element des Abarbeitungsschrittes
	 * @return erfolgreiche Abarbeitung (true/false)
	 * 
	 */
	public Boolean mAUTH(Robot robot, Element mStep) throws Exception {
		if (mStep.getChildText("realm") != null) {
			String username = mStep.getChildText("username") == null ? ""
					: mStep.getChildText("username");
			String password = mStep.getChildText("password") == null ? ""
					: mStep.getChildText("password");
			if (!robot.getPending(username).equals(""))
				username = robot.getPending(username);
			if (!robot.getPending(password).equals(""))
				password = robot.getPending(password);
			robot.setAuthentication(mStep.getChildText("realm"), username,
					password);
			return true;
		} // if
		return false;
	} // mAUTH
	
	
	
	
	
	
	
	//Hilfsmethoden
	/**
	 * Hilfmethode zu mPOST<br> 
	 * - gibt den entsprechenden Button im Formular zur�ck<br>
	 * - Spezifikation der Suchreihenfolge siehe mPOST
	 * 
	 *  @param form entsprechenden Formular
	 *  @param button spezifizierter Button
	 *  @return Button, wenn nicht vorhanden null
	 */
	private SubmitButton getSubmitButton (WebForm form, String button) throws Exception{
		SubmitButton sb = null;
		if (( button != null) && !button.equals("")) {
			sb = form.getSubmitButton(button);
			if (sb == null) {
				sb = form.getSubmitButtonWithID(button);
			} //if
			if (sb == null) {
				for (SubmitButton sb_elem : form.getSubmitButtons()) {
					if (sb_elem.getValue().equals(button)) {
						sb = sb_elem;
						break;
					} //if
				} //for
			} //if
		} //if name->ok
		return sb;
	}

} // class HTMLRobots