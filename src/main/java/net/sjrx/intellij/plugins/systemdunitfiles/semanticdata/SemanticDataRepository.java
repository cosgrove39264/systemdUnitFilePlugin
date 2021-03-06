package net.sjrx.intellij.plugins.systemdunitfiles.semanticdata;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.diagnostic.Logger;
import net.sjrx.intellij.plugins.systemdunitfiles.semanticdata.optionvalues.BooleanOptionValue;
import net.sjrx.intellij.plugins.systemdunitfiles.semanticdata.optionvalues.DocumentationOptionValue;
import net.sjrx.intellij.plugins.systemdunitfiles.semanticdata.optionvalues.KillModeOptionValue;
import net.sjrx.intellij.plugins.systemdunitfiles.semanticdata.optionvalues.ModeStringOptionValue;
import net.sjrx.intellij.plugins.systemdunitfiles.semanticdata.optionvalues.NullOptionValue;
import net.sjrx.intellij.plugins.systemdunitfiles.semanticdata.optionvalues.OptionValueInformation;
import net.sjrx.intellij.plugins.systemdunitfiles.semanticdata.optionvalues.RestartOptionValue;
import net.sjrx.intellij.plugins.systemdunitfiles.semanticdata.optionvalues.ServiceTypeOptionValue;
import org.apache.commons.io.IOUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SemanticDataRepository {
  
  
  private static final Logger LOG = Logger.getInstance(SemanticDataRepository.class);
  private static final String SEMANTIC_DATA_ROOT = "net/sjrx/intellij/plugins/systemdunitfiles/semanticdata/";
  private static final OptionValueInformation NULL_VALIDATOR = new NullOptionValue();
  private static final Map</* Section */ String, Map</* Key */ String, /* Validator */ String>> sectionToKeyAndValidatorMap
    = new TreeMap<>();
  private static final Pattern LINE_MATCHER = Pattern.compile("^(?<Section>[A-Z][a-z]+).(?<Key>\\w+),\\s*(?<Validator>\\w+)\\s*,.+$");
  private static SemanticDataRepository instance = null;
  private final Map<String, Map<String, Map<String, String>>> sectionNameToKeyValues;
  private final Map<String, OptionValueInformation> validatorMap;
  
  private SemanticDataRepository() {
    
    URL sectionToKeywordMapJsonFile =
      this.getClass().getClassLoader().getResource(SEMANTIC_DATA_ROOT + "sectionToKeywordMap.json");
    
    final ObjectMapper mapper = new ObjectMapper();
    
    try {
      sectionNameToKeyValues =
        mapper.readValue(sectionToKeywordMapJsonFile, new TypeReference<Map<String, Map<String, Map<String, String>>>>() {
        });
    } catch (IOException e) {
      throw new IllegalStateException("Unable to initialize data for systemd inspections plugin", e);
    }
  
    try (BufferedReader fr = new BufferedReader(new InputStreamReader(
      this.getClass().getClassLoader().getResourceAsStream(SEMANTIC_DATA_ROOT + "load-fragment-gperf.gperf")
    ))) {
      String line;
    
    
      while ((line = fr.readLine()) != null) {
      
        Matcher m = LINE_MATCHER.matcher(line);
      
        if (m.find()) {
          String section = m.group("Section");
          String key = m.group("Key");
          String validator = m.group("Validator");
        
          sectionToKeyAndValidatorMap.computeIfAbsent(section, k -> new TreeMap<>()).put(key, validator);
        }
      }
    
      OptionValueInformation[] ovis = {new BooleanOptionValue(),
        new DocumentationOptionValue(),
        new KillModeOptionValue(),
        new ModeStringOptionValue(),
        new RestartOptionValue(),
        new ServiceTypeOptionValue(),
        NULL_VALIDATOR };
      validatorMap = new HashMap<>();
      
      for (OptionValueInformation ovi : ovis) {
        validatorMap.put(ovi.getValidatorName(), ovi);
      }
      
    } catch (IOException e) {
      throw new IllegalStateException("Unable to initialize data for systemd inspections plugin", e);
    }
  }
  
  /**
   * Returns the allowed section names.
   *
   * @return a set of allow section names
   */
  public Set<String> getAllowedSectionNames() {
    return Collections.unmodifiableSet(sectionNameToKeyValues.keySet());
  }
  
  /**
   * Returns the allowed keywords by a given section name.
   *
   * @param section the section name (e.g., Unit, Install, Service)
   * @return set of allowed names.
   */
  public Set<String> getAllowedKeywordsInSection(String section) {
    return Collections.unmodifiableSet(this.getDataForSection(section).keySet());
  }
  
  /**
   * Returns the location (by keyword) for a specific keyword.
   * <p></p>
   * Some keys are documented together in systemd (e.g., After= and Before=), we can only
   * link to one of them, so this method will tell us what to link to.
   *
   * @param section - section name
   * @param keyName - key name
   * @return String or null
   */
  public String getKeywordLocationInDocumentation(String section, String keyName) {
    return this.getDataForSection(section).computeIfAbsent(keyName, k -> new HashMap<>())
             .computeIfAbsent("declaredUnderKeyword", v -> null);
  }
  
  /**
   * Returns the location in filename for a specific keyword.
   * <p></p>
   * Some keys are shared between many different types of units (e.g., WorkingDirectory= is in systemd.exec but shows up in [Service],
   * [Socket], etc...
   *
   * @param section - section name
   * @param keyName - key name
   * @return String or null
   */
  public String getKeywordFileLocationInDocumentation(String section, String keyName) {
    return this.getDataForSection(section).computeIfAbsent(keyName, k -> new HashMap<>())
             .computeIfAbsent("declaredInFile", v -> null);
  }
  
  private Map<String, Map<String, String>> getDataForSection(String section) {
    Map<String, Map<String, String>> sectionData = sectionNameToKeyValues.get(section);
    
    if (sectionData == null) {
      return Collections.emptyMap();
    } else {
      
      return sectionData;
    }
  }
  
  /**
   * Returns a URL to the section name man page.
   *
   * @param sectionName - the name of the section name
   * @return - best URL for the section name or null if the section is unknown
   */
  public String getUrlForSectionName(String sectionName) {
    switch (sectionName) {
      case "Mount":
        return "https://www.freedesktop.org/software/systemd/man/systemd.mount.html";
      case "Automount":
        return "https://www.freedesktop.org/software/systemd/man/systemd.automount.html";
      case "Install":
        return "https://www.freedesktop.org/software/systemd/man/systemd.unit.html#%5BInstall%5D%20Section%20Options";
      case "Path":
        return "https://www.freedesktop.org/software/systemd/man/systemd.path.html";
      case "Service":
        return "https://www.freedesktop.org/software/systemd/man/systemd.service.html";
      case "Slice":
        return "https://www.freedesktop.org/software/systemd/man/systemd.slice.html";
      case "Socket":
        return "https://www.freedesktop.org/software/systemd/man/systemd.socket.html";
      case "Swap":
        return "https://www.freedesktop.org/software/systemd/man/systemd.swap.html";
      case "Timer":
        return "https://www.freedesktop.org/software/systemd/man/systemd.timer.html";
      case "Unit":
        return "https://www.freedesktop.org/software/systemd/man/systemd.unit.html#%5BUnit%5D%20Section%20Options";
      default:
        return null;
    }
  }
  
  /**
   * Returns a document blurb for a Section.
   *
   * @param sectionName the name of the section.
   * @return string
   */
  public String getDocumentationContentForSection(String sectionName) {
    switch (sectionName) {
      case "Mount":
        return " Mount files must include a [Mount] section, which carries information\n"
               + "       about the file system mount points it supervises. A number of options\n"
               + "       that may be used in this section are shared with other unit types.\n"
               + "       These options are documented in <a href=\"http://man7.org/linux/man-pages/man5/systemd.exec.5.html\">"
               + "systemd.exec(5)</a> and <a href=\"http://man7.org/linux/man-pages/man5/systemd.kill.5.html\">systemd.kill(5)</a>.";
      
      case "Automount":
        return "Automount files must include an [Automount] section, which carries\n"
               + "       information about the file system automount points it supervises.";
      
      case "Install":
        return "Unit files may include an \"[Install]\" section, which carries\n"
               + "       installation information for the unit. This section is not\n"
               + "       interpreted by <a href=\"http://man7.org/linux/man-pages/man1/systemd.1.html\">systemd(1)</a> during runtime; it is"
               + "       used by the <b>enable</b> and <b>disable </b>commands of the"
               + "       <a href=\"http://man7.org/linux/man-pages/man1/systemctl.1.html\">systemctl(1)</a> tool during installation of\n"
               + "       a unit. ";
      case "Path":
        return "Path files must include a [Path] section, which carries information\n"
               + "       about the path(s) it monitors";
      
      case "Service":
        return "Service files must include a \"[Service]\" section, which carries\n"
               + "information about the service and the process it supervises. A number\n"
               + "of options that may be used in this section are shared with other\n"
               + "unit types. These options are documented in <a href=\"http://man7.org/linux/man-pages/man5/systemd.exec.5.html\">systemd.exec(5)</a>,\n"
               + "<a href=\"http://man7.org/linux/man-pages/man5/systemd.kill.5.html\">systemd.kill(5)</a> and "
               + "<a href=\"http://man7.org/linux/man-pages/man5/systemd.resource-control.5.html\">systemd.resource-control(5)</a>.";
      case "Slice":
        return "The slice specific configuration\n"
               + "       options are configured in the [Slice] section. Currently, only\n"
               + "       generic resource control settings as described in\n"
               + "       <a href=\"http://man7.org/linux/man-pages/man5/systemd.resource-control.5.html\">systemd.resource-control(5)</a> are allowed.\n";
      
      
      case "Socket":
        return "Socket files must include a [Socket] section, which carries\n"
               + "       information about the socket or FIFO it supervises. A number of\n"
               + "       options that may be used in this section are shared with other unit\n"
               + "       types. These options are documented in <a href=\"http://man7.org/linux/man-pages/man5/systemd.exec.5.html\">"
               + "       systemd.exec(5)</a> and <a href=\"http://man7.org/linux/man-pages/man5/systemd.kill.5.html\">systemd.kill(5)</a>.";
      case "Swap":
        return "Swap files must include a [Swap] section, which carries information\n"
               + " about the swap device it supervises. A number of options that may be\n"
               + " used in this section are shared with other unit types. These options\n"
               + " are documented in <a href=\"http://man7.org/linux/man-pages/man5/systemd.exec.5.html\">systemd.exec(5)</a>"
               + " and <a href=\"http://man7.org/linux/man-pages/man5/systemd.kill.5.html\">systemd.kill(5)</a>";
      
      case "Timer":
        return " Timer files must include a [Timer] section, which carries information\n"
               + " about the timer it defines.";
      
      case "Unit":
        return "The unit file may include a [Unit] section, which carries generic\n"
               + " information about the unit that is not dependent on the type of unit.";
      
      default:
        return null;
    }
  }
  
  /**
   * Return the documentation for this key.
   *
   * @param sectionName the section name to lookup (e.g., Unit, Install, Service)
   * @param keyName     the key name to look up
   * @return either the first paragraph from the HTML description or null if no description was found
   */
  public String getDocumentationContentForKeyInSection(String sectionName, String keyName) {
    
    InputStream htmlDocStream = this.getClass().getClassLoader().getResourceAsStream(SEMANTIC_DATA_ROOT + "/documents/completion/"
                                                                                     + sectionName + "/" + keyName + ".html");
    if (htmlDocStream == null) {
      return null;
    }
    
    try {
      return IOUtils.toString(htmlDocStream, "UTF-8");
    } catch (IOException e) {
      LOG.warn("Could not convert html document stream to String", e);
    }
    return null;
  }
  
  /**
   * Gets the validator for a section name and key name.
   *
   * @param sectionName the name of the section we are looking up
   * @param keyName - the keyname to look up
   * @return the validator
   */
  public OptionValueInformation getOptionValidator(String sectionName, String keyName) {
    String validatorName = sectionToKeyAndValidatorMap.getOrDefault(sectionName, Collections.emptyMap()).get(keyName);
    
    return validatorMap.getOrDefault(validatorName, NULL_VALIDATOR);
  }
  
  
  
  /**
   * Gets the Semantic Data Repository.
   *
   * @return singleton instance
   */
  public static synchronized SemanticDataRepository getInstance() {
    if (instance == null) {
      instance = new SemanticDataRepository();
    }
    
    return instance;
  }
}
