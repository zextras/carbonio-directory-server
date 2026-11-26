#!/usr/bin/perl

# SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
#
# SPDX-License-Identifier: GPL-2.0-only

use strict;
use warnings;
use lib '/opt/zextras/common/lib/perl5';
use Zextras::LdapMigrationUtils;

Zextras::LdapMigrationUtils::ensure_zextras_user();

sub wait_for_ldap {
    my $error = '';
    foreach my $attempt ( 1 .. 30 ) {
        my $ldap = eval { Zextras::LdapMigrationUtils::connect_ldap() };
        if ($ldap) {
            $ldap->unbind();
            return;
        }
        $error = $@;
        sleep 1;
    }
    print " * Error: LDAP is not reachable, post-schema migrations not applied: $error\n";
    exit(1);
}

sub apply_scripts {
    my ( $base_dir, @scripts ) = @_;
    foreach my $script (@scripts) {
        my $script_path = $base_dir . "/" . $script;
        print "** Applying changes from $script_path...\n";
        my $output = `$script_path 2>&1`;
        if ($?) {
            print " * Error applying changes from $script_path: $output\n";
            exit(1);
        }
        else {
            $output =~ s/^\s+|\s+$//g;
            if ($output) {
                print "\t$output\n";
            }
            print " * done.\n\n";
        }
    }
}

my $base_dir = "/opt/zextras/libexec/scripts/LDAP/migrations/post_schema/";
my @scripts  = ("01_user_management_acl.pl");

wait_for_ldap();
apply_scripts( $base_dir, @scripts );
