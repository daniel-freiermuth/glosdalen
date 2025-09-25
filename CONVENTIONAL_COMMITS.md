# Conventional Commits & Automatic Versioning

Glossordalen uses **Conventional Commits** with automatic semantic versioning powered by the **axion-release-plugin**.

## 🚀 How It Works

Your commit messages automatically determine version bumps:

- **`feat:`** → **Minor** version bump (1.0.0 → 1.1.0)
- **`fix:`** → **Patch** version bump (1.0.0 → 1.0.1) 
- **`BREAKING CHANGE:`** → **Major** version bump (1.0.0 → 2.0.0)

## 📝 Commit Message Format

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

### Types
- **feat**: A new feature
- **fix**: A bug fix
- **docs**: Documentation only changes
- **style**: Changes that do not affect the meaning of the code
- **refactor**: A code change that neither fixes a bug nor adds a feature
- **perf**: A code change that improves performance
- **test**: Adding missing tests or correcting existing tests
- **chore**: Changes to the build process or auxiliary tools

## 📋 Examples

### New Features (Minor Bump)
```bash
git commit -m "feat: add German-French translation support"
git commit -m "feat(ui): add dark mode toggle in settings"
git commit -m "feat(anki): support bidirectional card creation"
```

### Bug Fixes (Patch Bump)
```bash
git commit -m "fix: resolve TextField focus loss on text change"
git commit -m "fix(settings): language inconsistency when returning from settings"
git commit -m "fix(api): handle DeepL rate limiting correctly"
```

### Breaking Changes (Major Bump)
```bash
git commit -m "feat!: redesign API structure

BREAKING CHANGE: API endpoints have changed from /api/v1/ to /api/v2/
```

## 🏷️ Version Management

### Check Current Version
```bash
./gradlew currentVersion
```

### Create Release
```bash
./gradlew release
```

### Manual Version Bump
```bash
./gradlew markNextVersion -Prelease.version=1.2.0
```

## 🎯 Best Practices

1. **Be Descriptive**: Write clear, concise commit messages
2. **Use Scopes**: Add scope when working on specific modules (ui, api, anki, etc.)
3. **Breaking Changes**: Always document breaking changes in footer
4. **Atomic Commits**: One logical change per commit
5. **Test Before Commit**: Ensure builds pass

## 📊 Version History

- **v1.0.0**: Initial release with German-Swedish translation
- Future versions will be automatically generated based on your commits!

## 🛠️ Technical Details

- **Plugin**: [axion-release-plugin](https://github.com/allegro/axion-release-plugin)
- **Version Source**: Git tags + commit count
- **Build Integration**: Automatic `versionName` and `versionCode` in `build.gradle.kts`
- **Tag Format**: `v1.0.0` (semantic versioning with 'v' prefix)

Happy coding with automated versioning! 🎉
