const fs = require('fs');
const path = require('path');

const CONFIG = {
    inputDir: './',
    outputFile: 'merged.md',
    excludeDirs: ['node_modules', "app/build", ".gradle", "gradle", ".androidide", '.git', 'dist', "webui", "pets", "plugin", "restored_project"],
    extensions: ['.js', ".java", ".xml", '.json', '.ts', '.css', '.html', '.md', '.txt', '.vue']
};

/**
 * 转义代码块中的反引号，确保 Markdown 渲染正常
 * 原理：如果内容中包含 ```，我们在外层使用更多的反引号（如 ````）
 */
function wrapCode(content, ext) {
    // 寻找内容中连续反引号的最大数量
    const backtickMatch = content.match(/`{3,}/g);
    const maxBackticks = backtickMatch 
        ? Math.max(...backtickMatch.map(m => m.length)) 
        : 2;
    
    // 外层包裹的数量必须比内容中最长的还要多一个
    const wrapper = '`'.repeat(maxBackticks + 1);
    return `${wrapper}${ext}\n${content}\n${wrapper}`;
}

function getFilesRecursively(dir, fileList = []) {
    const files = fs.readdirSync(dir);
    files.forEach(file => {
        const filePath = path.join(dir, file);
        const stat = fs.statSync(filePath);

        if (stat.isDirectory()) {
            if (!CONFIG.excludeDirs.includes(file)) {
                getFilesRecursively(filePath, fileList);
            }
        } else {
            const ext = path.extname(file);
            if (CONFIG.extensions.includes(ext) && file !== CONFIG.outputFile) {
                fileList.push(filePath);
            }
        }
    });
    return fileList;
}

function mergeFiles() {
    const allFiles = getFilesRecursively(CONFIG.inputDir);
    let combinedContent = `# Project Source Code\n\nGenerated on: ${new Date().toLocaleString()}\n\n---\n\n`;

    allFiles.forEach(filePath => {
        try {
            const content = fs.readFileSync(filePath, 'utf8');
            const relativePath = path.relative(process.cwd(), filePath);
            const ext = path.extname(filePath).slice(1);

            combinedContent += `## File: \`${relativePath}\`\n\n`;
            // 使用处理过的包裹逻辑
            combinedContent += wrapCode(content, ext);
            combinedContent += `\n\n---\n\n`;
        } catch (err) {
            console.error(`无法读取文件 ${filePath}: ${err.message}`);
        }
    });

    fs.writeFileSync(CONFIG.outputFile, combinedContent);
    console.log(`✅ 成功！处理了 ${allFiles.length} 个文件。`);
}

mergeFiles();