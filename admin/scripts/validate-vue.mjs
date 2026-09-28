import { readdirSync, readFileSync } from 'node:fs'
import { join, relative } from 'node:path'
import { compileScript, compileStyleAsync, compileTemplate, parse } from '@vue/compiler-sfc'

const sourceRoot = join(process.cwd(), 'src')
const vueFiles = collectVueFiles(sourceRoot)
const failures = []

for (const file of vueFiles) {
  const filename = relative(process.cwd(), file).replaceAll('\\', '/')
  const source = readFileSync(file, 'utf8')
  const { descriptor, errors } = parse(source, { filename })
  collectErrors(filename, errors)

  if (descriptor.script || descriptor.scriptSetup) {
    try {
      compileScript(descriptor, { id: filename })
    } catch (error) {
      failures.push(`${filename}: ${error.message}`)
    }
  }

  if (descriptor.template) {
    const result = compileTemplate({
      id: filename,
      filename,
      source: descriptor.template.content,
    })
    collectErrors(filename, result.errors)
  }

  for (const style of descriptor.styles) {
    const result = await compileStyleAsync({
      id: filename,
      filename,
      source: style.content,
      scoped: style.scoped,
    })
    collectErrors(filename, result.errors)
  }
}

if (failures.length > 0) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log(`Vue SFC check passed: ${vueFiles.length} files`)

function collectVueFiles(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const path = join(directory, entry.name)
    if (entry.isDirectory()) return collectVueFiles(path)
    return entry.isFile() && entry.name.endsWith('.vue') ? [path] : []
  })
}

function collectErrors(filename, errors = []) {
  for (const error of errors) {
    failures.push(`${filename}: ${typeof error === 'string' ? error : error.message}`)
  }
}
