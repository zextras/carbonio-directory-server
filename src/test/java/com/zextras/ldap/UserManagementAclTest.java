package com.zextras.ldap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UserManagementAclTest {

	private static final String USER_MANAGEMENT = "dn.base=\"uid=carbonio-user-management,cn=appaccts,cn=zimbra\"";
	private static final Path MDB_LDIF = Path.of("src/ldap/config/cn=config/olcDatabase={2}mdb.ldif");
	private static final List<String> SETTING_PREFIXES = List.of("carbonioFeature", "carbonioWsc", "carbonioFiles",
			"carbonioTasks", "carbonioDocs", "carbonioPreview");
	private static final Set<String> STANDARD_ATTRIBUTES = Set.of("mail", "cn", "displayName");

	@Test
	void readRuleCoversEveryFeatureAndCapabilitySetting() throws IOException {
		Set<String> granted = grantedAttributes();
		Set<String> missing = new TreeSet<>();
		for (String name : catalogAttributes()) {
			if (SETTING_PREFIXES.stream().anyMatch(name::startsWith) && !granted.contains(name)) {
				missing.add(name);
			}
		}
		Assertions.assertTrue(missing.isEmpty(), "Not readable by user management: " + missing);
	}

	@Test
	void readRuleNamesOnlyKnownAttributes() throws IOException {
		Set<String> catalog = catalogAttributes();
		Set<String> unknown = new TreeSet<>();
		for (String name : grantedAttributes()) {
			if (!catalog.contains(name) && !STANDARD_ATTRIBUTES.contains(name)) {
				unknown.add(name);
			}
		}
		Assertions.assertTrue(unknown.isEmpty(), "Unknown attributes: " + unknown);
	}

	@Test
	void readRuleIsLastAndBreaksForEveryoneElse() throws IOException {
		List<String> rules = rules();
		String last = rules.get(rules.size() - 1);
		Assertions.assertTrue(last.contains(USER_MANAGEMENT + " read"), last);
		Assertions.assertTrue(last.endsWith("by * +0 break"), last);
	}

	@ParameterizedTest
	@ValueSource(ints = {1, 2, 8, 9})
	void userManagementPassesThroughBeforeAnyOtherClause(int index) throws IOException {
		String rule = rules().get(index);
		int firstBy = rule.indexOf("  by ");
		Assertions.assertTrue(rule.startsWith(USER_MANAGEMENT + " +0 break", firstBy + "  by ".length()), rule);
	}

	@ParameterizedTest
	@ValueSource(ints = {3, 7, 8})
	void rulesRewrittenByLdapAnonKeepTheirIndex(int index) throws IOException {
		String rule = rules().get(index);
		String expected = switch (index) {
			case 3 -> "to attrs=objectclass ";
			case 7 -> "to filter=\"(!(zimbraHideInGal=TRUE))\" ";
			default -> "to attrs=zimbraId,";
		};
		Assertions.assertTrue(rule.startsWith(expected), rule);
	}

	private static Set<String> grantedAttributes() throws IOException {
		List<String> rules = rules();
		Matcher matcher = Pattern.compile("^to attrs=(\\S+)\\s").matcher(rules.get(rules.size() - 1));
		Assertions.assertTrue(matcher.find());
		return new LinkedHashSet<>(Arrays.asList(matcher.group(1).split(",")));
	}

	private static List<String> rules() throws IOException {
		List<String> lines = new ArrayList<>();
		for (String line : Files.readAllLines(MDB_LDIF)) {
			if (line.startsWith(" ") && !lines.isEmpty()) {
				lines.set(lines.size() - 1, lines.get(lines.size() - 1) + line.substring(1));
			} else {
				lines.add(line);
			}
		}
		List<String> rules = new ArrayList<>();
		for (String line : lines) {
			if (line.startsWith("olcAccess: ")) {
				String rule = line.substring("olcAccess: ".length());
				Assertions.assertTrue(rule.startsWith("{" + rules.size() + "}"), rule);
				rules.add(rule.substring(rule.indexOf('}') + 1));
			}
		}
		return rules;
	}

	private static Set<String> catalogAttributes() throws IOException {
		Set<String> names = new TreeSet<>();
		String catalog = Files.readString(Path.of("src/main/resources/conf/attrs/attrs.xml"), StandardCharsets.UTF_8);
		Matcher matcher = Pattern.compile("<attr\\s[^>]*\\bname=\"([^\"]+)\"").matcher(catalog);
		while (matcher.find()) {
			names.add(matcher.group(1));
		}
		Assertions.assertTrue(names.size() > 1000, "attrs.xml not parsed");
		return names;
	}
}
