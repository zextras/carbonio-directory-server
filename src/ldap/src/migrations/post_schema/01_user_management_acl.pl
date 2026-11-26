#!/usr/bin/perl

# SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
#
# SPDX-License-Identifier: GPL-2.0-only

use strict;
use warnings;
use lib '/opt/zextras/common/lib/perl5';
use Zextras::LdapMigrationUtils;

my $user_management_dn = 'uid=carbonio-user-management,cn=appaccts,cn=zimbra';
my $source_ldif =
  '/opt/zextras/common/etc/openldap/zimbra/config/cn=config/olcDatabase={2}mdb.ldif';

Zextras::LdapMigrationUtils::ensure_zextras_user();

sub without_index {
    my ($rule) = @_;
    $rule =~ s/^\{\d+\}//;
    return $rule;
}

sub normalized {
    my ($rule) = @_;
    $rule = without_index($rule);
    $rule =~ s/\s+/ /g;
    $rule =~ s/^ | $//g;
    return $rule;
}

sub split_rule {
    my ($rule) = @_;
    $rule = without_index($rule);
    $rule =~ /^(.*?)(\s+by\s.*)$/s or die "Unexpected olcAccess value: $rule\n";
    return ( $1, $2 );
}

sub source_rules {
    open( my $in, '<', $source_ldif ) or die "Cannot read $source_ldif: $!\n";
    my @lines;
    while ( my $line = <$in> ) {
        chomp $line;
        if ( $line =~ /^ (.*)$/ && @lines ) {
            $lines[-1] .= $1;
        }
        else {
            push @lines, $line;
        }
    }
    close $in;
    return map { /^olcAccess: (.*)$/ ? without_index($1) : () } @lines;
}

sub main_database {
    my ($ldap) = @_;
    my $mesg = $ldap->search(
        base   => 'cn=config',
        scope  => 'one',
        filter => '(objectClass=olcMdbConfig)',
        attrs  => [ 'olcSuffix', 'olcAccess' ],
    );
    $mesg->code && die 'Search: ' . $mesg->error . "\n";
    foreach my $entry ( $mesg->entries ) {
        my $suffix = $entry->get_value('olcSuffix');
        return $entry if !defined $suffix || $suffix eq '';
    }
    die "No mdb database with an empty suffix found under cn=config\n";
}

sub live_rules {
    my ($entry) = @_;
    my @rules;
    foreach my $value ( $entry->get_value('olcAccess') ) {
        $value =~ /^\{(\d+)\}/ or die "Unexpected olcAccess value: $value\n";
        $rules[$1] = $value;
    }
    return @rules;
}

sub replace_rule {
    my ( $ldap, $dn, $index, $rule ) = @_;
    my $mesg = $ldap->modify(
        $dn,
        changes => [
            delete => [ olcAccess => ["{$index}"] ],
            add    => [ olcAccess => ["{$index}$rule"] ],
        ],
    );
    $mesg->code && die "Modify olcAccess {$index}: " . $mesg->error . "\n";
}

my @wanted = grep { index( $_, $user_management_dn ) >= 0 } source_rules();
die "No rule for $user_management_dn in $source_ldif\n" unless @wanted;

my $ldap     = Zextras::LdapMigrationUtils::connect_ldap();
my $database = main_database($ldap);
my $dn       = $database->dn();
my @live     = live_rules($database);
my $changes  = 0;

foreach my $rule (@wanted) {
    my ( $what, $who ) = split_rule($rule);
    my $grant = $who =~ /^\s+by\s+dn\.base="\Q$user_management_dn\E"\s+read\b/;

    if ($grant) {
        my ($index) =
          grep { defined $live[$_] && $live[$_] =~ /dn\.base="\Q$user_management_dn\E"\s+read\b/ }
          0 .. $#live;
        if ( !defined $index ) {
            my $mesg = $ldap->modify( $dn, add => { olcAccess => [$rule] } );
            $mesg->code && die 'Add olcAccess: ' . $mesg->error . "\n";
            print "* Added the $user_management_dn read rule\n";
            $changes++;
        }
        elsif ( normalized( $live[$index] ) ne normalized($rule) ) {
            replace_rule( $ldap, $dn, $index, $rule );
            print "* Updated the $user_management_dn read rule {$index}\n";
            $changes++;
        }
        next;
    }

    my ($index) =
      grep { defined $live[$_] && normalized( ( split_rule( $live[$_] ) )[0] ) eq normalized($what) }
      0 .. $#live;
    if ( !defined $index ) {
        print "* Warning: no olcAccess rule '$what' in $dn, $user_management_dn not added to it\n";
        next;
    }
    next if index( $live[$index], $user_management_dn ) >= 0;

    my ( $live_what, $live_who ) = split_rule( $live[$index] );
    replace_rule( $ldap, $dn, $index,
        "$live_what  by dn.base=\"$user_management_dn\" +0 break$live_who" );
    print "* Let $user_management_dn through olcAccess {$index}\n";
    $changes++;
}

print "* olcAccess for $user_management_dn already up to date\n" unless $changes;
$ldap->unbind();
