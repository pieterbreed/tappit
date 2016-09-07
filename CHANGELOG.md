# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## Planned
### Added
- `prove` that inspects a TAP stream and reports on success or failure.
- `TAP` text parser that can turn text into a tap data stream, for use with `prove`.

## [Unreleased]

## [0.9.7] - 2016-09-07

### Changed
- `tappit.reducer` - multi-method/reduce-based implementation for the tap producer
- Change `tappit.producer!` to use the `tappit.reducer` api instead of the complected `*out*` and `atom`-based implementation I started with.
- BREAKING! `[org.clojure/clojure "1.9.0-alpha10"]`

## [0.9.6] - 2016-09-05
### Added
- travis ci
### Changed
- protocol based api for the leaky defmacro `with-tap!`

## [0.9.5] - 2016-09-03
### Fixed
- Undid the cleverness of turning passing skips into bonus comments
### Added
- `isa!` and `=!`
- `bail-out!`, `not-bailed?` and `bailed?`'
- Enough API to make examples 2, 3 and 4 pass too.

## [0.9.4] - 2016-09-02
### Fixed
- fixed minor issues with the `doc/example1.boot` script

## [0.9.0] - 2016-09-02
### Added
- Enough API to make `doc/example1.boot` pass.

[Unreleased]: https://github.com/pieterbreed/tappit/compare/tappit-0.9.7...HEAD
[0.9.7]: https://github.com/pieterbreed/tappit/compare/tappit-0.9.6...tappit-0.9.7
[0.9.6]: https://github.com/pieterbreed/tappit/compare/tappit-0.9.5...tappit-0.9.6
[0.9.5]: https://github.com/pieterbreed/tappit/compare/tappit-0.9.0...tappit-0.9.5
[0.9.4]: https://github.com/pieterbreed/tappit/compare/tappit-0.9.0...tappit-0.9.4
[0.9.0]: https://github.com/pieterbreed/tappit/compare/540aeff...tappit-0.9.0
 
