'use strict'

// `{project-version}` in the dependency snippets must be the version of the docs being read. The
// CI passes the POM version as a soft-set default (`project-version@`), which is right for `main`;
// a release tag is named after its Maven version (deploy-docs.yml checks that), so each one
// takes its own name instead of whatever version the build that published the site had.
module.exports.register = function () {
  this.on('contentClassified', ({ contentCatalog }) => {
    for (const component of contentCatalog.getComponents()) {
      for (const componentVersion of component.versions) {
        if (componentVersion.version === 'main') continue
        // A new object: the versions of a component may share the same AsciiDoc config object.
        const attributes = Object.assign({}, (componentVersion.asciidoc || {}).attributes, { 'project-version': componentVersion.version })
        componentVersion.asciidoc = Object.assign({}, componentVersion.asciidoc, { attributes })
      }
    }
  })
}
