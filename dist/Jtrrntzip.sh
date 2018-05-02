#!/bin/sh
dir=$(cd -P -- "$(dirname -- "$0")" && pwd -P)
java -jar Jtrrntzip.jar "$@"
