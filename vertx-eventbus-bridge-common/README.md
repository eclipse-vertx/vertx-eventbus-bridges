# vert.x bridge common

[![Build Status (5.x)](https://github.com/vert-x3/vertx-bridge-common/actions/workflows/ci-5.x.yml/badge.svg)](https://github.com/vert-x3/vertx-bridge-common/actions/workflows/ci-5.x.yml)
[![Build Status (4.x)](https://github.com/vert-x3/vertx-bridge-common/actions/workflows/ci-4.x.yml/badge.svg)](https://github.com/vert-x3/vertx-bridge-common/actions/workflows/ci-4.x.yml)

This repository contains the common configuration for event bus bridges.

## Common configuration

This projects contains two data objects:

* Bridge Options
* Permitted Options

`BridgeOptions` maintains the list of inbound and outbound permitted options, while `PermittedOptions` describes an address that is accepted by the bridge.

Permitted options be used to match:

* an exact address
* an address that match a regular expression
* messages that match a specific structure (generally use in addition to one of the other option).


