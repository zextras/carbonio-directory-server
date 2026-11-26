# post_schema

This directory should contain scripts that are supposed to be executed after the new LDAP schema is installed and slapd is restarted on upgrade.
The scripts are executed by LdapPostSchema.pl in the order defined inside it.

## Use Cases

- Modify LDAP configuration (cn=config) that refers to attributes added by the new schema, such as access control rules.
