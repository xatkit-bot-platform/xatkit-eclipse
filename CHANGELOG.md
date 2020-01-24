# Changelog

All notable changes for the Xatkit Eclipse plugins will be documented in this file.

Note that there is no changelog available for the initial release of the platform (2.0.0), you can find the release notes [here](https://github.com/xatkit-bot-platform/xatkit-eclipse/releases).

The changelog format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres to [Semantic Versioning](https://semver.org/v2.0.0.html)

## Unreleased

### Changed

- Change log level of non-critical message related to DSL parsing and model loading. This reduces the amount of noise in Xatkit logs.

### Fixed

- [#23](https://github.com/xatkit-bot-platform/xatkit-eclipse/issues/23): *Most of model loading logs should be trace/debug*

## [3.0.0] - 2019-12-01

### Added

- *Platform* language now supports types for *Action* parameters and *Action* return types. These types are optional, and will be inferred as `Object` is no type is specified. Types must refer to JVM type (e.g. a type loaded from the classpath).
- Optional `from <PlatformName>` clause in execution rule: this clause allows to filter events/intents based on the platform that produced them. This feature can be useful for bots interacting with multiple messaging platforms. This clause is optional: if it is not specified the engine will match the event regardless the platform that produced them. 
- *Empty Xatkit Project* wizard, accessible through `File > New > Project > Xatkit`. The wizard creates a Xatkit project containing `intent`, `execution`, and `properties` file examples describing a simple *Hello World* bot definition that can be run with Xatkit.

### Changed

- All the grammars now inherits from the *Xbase* grammar, this allows to integrate *Xbase* expressions in execution models.
- Action invocation doesn't need to be prefixed with the  `action` keyword. **This change breaks the public API**: execution models containing the `action` keyword won't be valid anymore.
- Refactored `ImportRegistry` and renamed it `XatkitImportHelper` to make it stateless and usable from both the runtime component and the eclipse plugins. The code was a mess, it should be a bit better now. **This change breaks the public API**.
- `intent`, `execution`, and `platform` files now have a dedicated icon.

### Removed

- Removed *common expressions* from the *common* grammar. The grammar now extends *Xbase* and doesn't need its own expression language. **This change breaks the public API**: execution models using the *common expression language* won't be valid anymore.

## [2.1.0] - 2019-10-10

### Changed

- Intent libraries are now loaded the same way as platforms (using `$XATKIT/plugins/libraries/` directory). This change doesn't break the public API if the latest version of Xatkit is installed. See [this issue](https://github.com/xatkit-bot-platform/xatkit-eclipse/issues/18) for additional information.
- The projects now has an explicit dependency to [xatkit-metamodels](https://github.com/xatkit-bot-platform/xatkit-metamodels). This change doesn't break the public API if the latest version of Xatkit is installed.

## [2.0.0] - 2019-08-20 

See the release notes [here](https://github.com/xatkit-bot-platform/xatkit-eclipse/releases).
