# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## Planned
### Changed
- protocol based api instead of the leaky defmacro for `with-tap!`
## [Unreleased]
### Added
- generative testing harness for acceptance testing
- travis ci

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

[Unreleased]: https://github.com/pieterbreed/tappit/compare/tappit-0.9.5...HEAD
[0.9.5]: https://github.com/pieterbreed/tappit/compare/tappit-0.9.0...tappit-0.9.5
[0.9.4]: https://github.com/pieterbreed/tappit/compare/tappit-0.9.0...tappit-0.9.4
[0.9.0]: https://github.com/pieterbreed/tappit/compare/540aeff...tappit-0.9.0
 
