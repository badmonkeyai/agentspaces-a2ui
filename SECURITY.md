# Security Policy

AgentSpaces A2UI defines what an agent writes into a space for people to see,
and what a renderer writes back when a person acts on it. Hosts and renderers
trust these records to tell them what to draw and what to serve, so the records
carry part of that trust: a static app's paths, its media types, and the files
its bundle may hold. It inherits the security model of the AgentSpaces core
(signed, self-certifying peers and agents; the threat model in the AgentSpaces
specification, SPEC §11).

## Reporting a vulnerability

Please report vulnerabilities **privately** via GitHub's security advisories:

> https://github.com/badmonkeyai/agentspaces-a2ui/security/advisories/new

A vulnerability in the space API, the wire codec, peering, or signatures
belongs in the core repository's advisories:
https://github.com/badmonkeyai/agentspaces/security/advisories/new

Do not open public issues for suspected vulnerabilities, and do not include
exploit details in public discussions until a fix is released.

We will acknowledge your report within five business days, keep you informed of
progress, credit you in the advisory unless you prefer otherwise, and
coordinate the disclosure timeline with you. There is currently no bug bounty
program.

Questions can be sent to `oss [at] badmonkey.ai`

## Scope

In scope: anything in this repository that lets an attacker make a host or a
renderer that follows these records do what the records say it must not. For
example:
- a `PublishStatic` path that `isPath` accepts and that leaves the app's
  directory, names a hidden file, or needs escaping in a URL;
- a `StaticBundle` that `open` returns with a file its publish does not name,
  or without one it does;
- bytes that `StaticBundle.decode` accepts although they are not a bundle's
  canonical encoding;
- a media type the `PublishStatic` check accepts outside RFC 6838's form;
- a record built with a null app or surface id, which the hosts that sort and
  tag by them rely on never seeing;
- a catalog in this jar whose rules tell a renderer to treat a data model value
  as markup or script.

Out of scope: renderers and app hosts, which live in their own projects, and
what an agent chooses to draw. An agent that a group admits may write any
surface its group allows; who may join and write is the group's admission
policy, which the core enforces.

## Supported versions

During 0.x, only the latest minor release receives security fixes.
