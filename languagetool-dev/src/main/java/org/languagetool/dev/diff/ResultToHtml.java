/* LanguageTool, a natural language style checker
 * Copyright (C) 2020 Daniel Naber (http://www.danielnaber.de)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301
 * USA
 */
package org.languagetool.dev.diff;

import org.jetbrains.annotations.NotNull;
import org.languagetool.JLanguageTool;
import org.languagetool.Language;
import org.languagetool.Languages;
import org.languagetool.rules.Rule;
import org.languagetool.tools.StringTools;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Converts plain text results of Main or SentenceSourceChecker to HTML, sorted by rule id.
 */
public class ResultToHtml {

  private static final int THRESHOLD = 0;

  private final Map<String, String> ruleIdToCategoryId = new HashMap<>();

  private FileWriter fw;

  public ResultToHtml(Language lang) {
    JLanguageTool lt = new JLanguageTool(lang);
    for (Rule rule : lt.getAllRules()) {
      ruleIdToCategoryId.put(rule.getId(), rule.getCategory().getId().toString());
    }
  }

  public void run(String inputFile, String outputFile) throws IOException {
    try {
      fw = new FileWriter(outputFile);
      LightRuleMatchParser parser = new LightRuleMatchParser();
      List<LightRuleMatch> matches = parser.parseOutput(new File(inputFile));
      matches.sort((k, v) -> {
          String catIdK = getCategoryId(k);
          String catIdV = getCategoryId(v);
          if (catIdK.equals(catIdV)) {
            return k.getFullRuleId().compareTo(v.getFullRuleId());
          } else {
            return catIdK.compareTo(catIdV);
          }
        }
      );
      printHtml(inputFile, matches);
    } finally {
      fw.close();
    }
  }

  private void printHtml(String filename, List<LightRuleMatch> matches) throws IOException {
    print("<!doctype html>");
    print("<!-- generated by " + ResultToHtml.class.getSimpleName() + " on " + new Date() + " -->");
    print("<html>");
    print("<head>");
    print("  <title>Sorted " + filename + "</title>");
    print("  <meta http-equiv=\"content-type\" content=\"charset=utf-8\">");
    print("  <style>");
    print("    .sentence { color: #000; }");
    print("    .message { color: #777; }");
    print("    .marker { text-decoration: underline; background-color: #ffe8e8 }");
    print("    li { margin-bottom: 8px; }");
    print("  </style>");
    print("</head>");
    print("<body>");
    print(matches.size() + " total matches<br>");
    Map<String, Integer> matchToCount = getMatchToCount(matches);
    printToc(matches, matchToCount);
    String prevRuleId = "";
    String prevCategoryId = "";
    boolean listStarted = false;
    int skipped = 0;
    for (LightRuleMatch match : matches) {
      String categoryId = getCategoryId(match);
      if (!match.getFullRuleId().equals(prevRuleId)) {
        if (listStarted) {
          print("</ol>");
        }
        if (!categoryId.equals(prevCategoryId)) {
          print("<h1>Category " + categoryId + "</h1>");
        }
        Integer count = matchToCount.get(match.getFullRuleId());
        if (count >= THRESHOLD) {
          String tempOff = match.getStatus() == LightRuleMatch.Status.temp_off ? "[temp_off]" : "";
          print("<a name='" + match.getFullRuleId() + "'></a><h3>" + match.getFullRuleId() + " " + tempOff + " (" + count + " matches)</h3>");
          print("Source: " + match.getRuleSource() + "<br><br>");
          print("<ol>");
          listStarted = true;
        } else {
          skipped++;
        }
      }
      print("<li>");
      print("  <span class='message'>" + match.getMessage() + "</span><br>");
      print("  <span class='sentence'>" + StringTools.escapeHTML(match.getContext())
        .replaceFirst("&lt;span class='marker'&gt;", "<span class='marker'>")
        .replaceFirst("&lt;/span&gt;", "</span>")
        + "</span><br>");
      print("</li>");
      prevRuleId = match.getFullRuleId();
      prevCategoryId = categoryId;
    }
    print("</ol>");
    print("Note: " + skipped + " rules have been skipped because they matched fewer than " + THRESHOLD + " times");
    print("</body>");
    print("</html>");
  }

  @NotNull
  private String getCategoryId(LightRuleMatch match) {
    String categoryId = ruleIdToCategoryId.get(match.getRuleId());
    if (categoryId == null) {
      categoryId = "unknown";  // some rules cannot be mapped, as the rule ids might have changes since the input was generated
    }
    return categoryId;
  }

  private void printToc(List<LightRuleMatch> matches, Map<String, Integer> matchToCount) throws IOException {
    String prevRuleId = "";
    String prevCategoryId = "";
    print("<h1>TOC</h1>");
    Map<String, Integer> rulesInCategory = new HashMap<>();
    for (LightRuleMatch match : matches) {
      String ruleId = match.getFullRuleId();
      String categoryId = getCategoryId(match);
      if (!ruleId.equals(prevRuleId)) {
        if (!categoryId.equals(prevCategoryId)) {
          printRulesInCategory(rulesInCategory);
          rulesInCategory.clear();
          print("<h3>Category " + categoryId + "</h3>");
        }
        Integer count = matchToCount.get(ruleId);
        if (count >= THRESHOLD) {
          rulesInCategory.put(ruleId, count);
          //print("<a href='#" + ruleId + "'>" + ruleId + " (" + count + ")</a><br>");
        }
      }
      prevRuleId = ruleId;
      prevCategoryId = categoryId;
    }
    printRulesInCategory(rulesInCategory);
    print("<br>");
  }
  
  private void printRulesInCategory(Map<String, Integer> rulesInCategory) {
    if (rulesInCategory.size() > 0) {
      Map<String, Integer> sorted = rulesInCategory.entrySet().stream()
          .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
      sorted.forEach((k, v) -> {
        try {
          print("<a href='#" + k + "'>" + k + " (" + v + ")</a><br>");
        } catch (IOException e) {
          e.printStackTrace();
        }
      });
    }
  }

  private Map<String, Integer> getMatchToCount(List<LightRuleMatch> matches) {
    Map<String, Integer> catToCount = new HashMap<>();
    for (LightRuleMatch match : matches) {
      String id = match.getFullRuleId();
      if (catToCount.containsKey(id)) {
        catToCount.put(id, catToCount.get(id) + 1);
      } else {
        catToCount.put(id, 1);
      }
    }
    return catToCount;
  }

  private void print(String s) throws IOException {
    //System.out.println(s);
    fw.write(s);
    fw.write('\n');
  }

  public static void main(String[] args) throws IOException {
    if (args.length != 3) {
      System.out.println("Usage: " + ResultToHtml.class.getSimpleName() + " <langCode> <plainTextResult> <outputFile>");
      System.out.println("  <plainTextResult> is the result of e.g. Main or SentenceSourceChecker");
      System.exit(1);
    }
    ResultToHtml prg = new ResultToHtml(Languages.getLanguageForShortCode(args[0]));
    prg.run(args[1], args[2]);
  }
}
