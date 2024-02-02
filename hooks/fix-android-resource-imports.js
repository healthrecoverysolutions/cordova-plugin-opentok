#!/usr/bin/env node

const fs = require('fs');
const path = require('path');

const cordovaAndroidPath = [`platforms`, `android`, `app`, `src`, `main`, `java`, `com`, `tokbox`, `cordova`];

function extractBundleIdFromConfigXml(configXmlPath) {
    const pattern = /<widget id="([^"]+)"/;
    const data = fs.readFileSync(configXmlPath, 'utf8');
    return pattern.exec(data)[1];
}

function rewriteResourceImports(bundleId, sourceFileDirectory, sourceFileNames) {
    for (const fileName of sourceFileNames) {
        console.log(`rewriting resource imports for ${fileName} in directory ${sourceFileDirectory}`);
        const filePath = path.resolve(sourceFileDirectory, fileName);
        const input = fs.readFileSync(filePath, 'utf8');
        const output = input.replace(/import com\.hrs\.patient\.R;/gm, `import ${bundleId}.R;`);
        fs.writeFileSync(filePath, output, 'utf8');
    }
}

function main(context) {
    const cdvRoot = context && context.opts && context.opts.projectRoot;
    const projectRoot = cdvRoot || process.cwd();
    const configXmlPath = path.resolve(projectRoot, 'config.xml');
    const bundleId = extractBundleIdFromConfigXml(configXmlPath);
    console.log(`extracted app bundle id: ${bundleId}`);
    const sourceFileDirectory = path.resolve(projectRoot, ...cordovaAndroidPath);
    rewriteResourceImports(bundleId, sourceFileDirectory, [`VonageActivity.java`]);
}

module.exports = main;