# WATR-works
A set of related projects to extract text from PDFs

[WatrWorks Full Documentation (WIP)](http://iesl.github.io/watr-works/index.html)

## Running the command-line app, bin/works

From the project root,
> bin/works [cmd] [args]

For help,
> bin/works --help

## Corpus structure and initialization

bin/works expects the PDFs to be organized such that there is one subdirectory per PDF,
and all generated artifacts (as well as the original PDF) are placed together in the appropriate
artifacts directory, like so:

    .
    ├── .corpus-root
    ├── 0575.pdf.d
    │   ├── 0575.pdf
    │   ├── bbox.svg
    │   ├── docseg.json
    │   ├── lineseg.txt
    │   └── paragraphs.txt
    ├── 1483.pdf.d
    │   ├── 1483.pdf
    │   ├── bbox.svg
    │   ├── docseg.json
    │   ├── lineseg.txt
    │   └── paragraphs.txt
    └── 2839.pdf.d
        ├── 2839.pdf
        ├── lineseg.txt
        └── paragraphs.txt



bin/works can initialize directory filled with PDFs (all in the root of the directory), by creating the
subdirectories (hereafter called artifact directories), and stashing each pdf in its artifact directory.

There is a marker file named '.corpus-root' placed in the root directory. It is created by bin/works
when initializing a corpus, and later checked for when processing corpus entries. To initialize:

> bin/works init --corpus ./path/to/dir/with/pdfs

You can add new pdfs to the root of the corpus directory and re-run init without disturbing
existing entries.



## Selecting corpus entries to process

By default, bin/works will process everything in the specified corpus directory. If you wish to process a
subset of those files, you can use the --inputs=filename flag to specify a set of entries in the corpus to
process. The entries are specified using the directory name generated by bin/works --init, so the command

> bin/works docseg --corpus ./path/to/dir/with/pdfs --inputs=myfiles.txt

where myfile.txt contains 2 lines:

    1483.pdf.d
    2839.pdf.d

would docseg those entries.



## Ways to process a corpus

There are a number of processes that may be run on a corpus. The primary process is document segmentation,
which is run like so:

> bin/works docseg --corpus ./path/to/initd/corpus

Run bin/works --help to see the list of runnable processes. The primary processes are documented below.


## Document Segmentation

### Output Format for Document Segmentation

Segmentation produces a json file like this:


    { "pages": [
        {"page": 0,
         "blocks": [
           {"labels": ["header", "publisher"],
            "lines": [
              [["©","2014","Elsevier","B.V.","All","rights","reserved."],     [0,1,2,3,4,5,6]]
            ]},
           {"labels": ["body", "section-heading"],
            "lines": [
              [["1.","Introduction"],     [7,8]]
           ]},
           {"labels": ["body", "paragraph"],
            "lines": [
              [["{PhSiH_{3}}","into","the","corresponding","phosphines."],     [643,644,645,646,647]],
          ]
        },
        {"page": 1,
         "blocks": [
             ...
         ]
        },
     },
     "ids": [
        [0,[0, [426.41, 556.48, 5.90, 7.17]]],[1,[0, [434.17, 556.48, 17.26, 7.17]]],
        [10,[0, [367.43, 687.76, 14.25, 7.97]]],[11,[0, [383.27, 687.76, 56.16, 7.97]]]
     ]}


Each page consists of a list of blocks, where each block is a labeled set of text lines. The labels identify the section
of the paper where the text appeared (header, body, or references), and, if it can be determined, the role of that
block of text (section-heading, paragraph, caption, table). The text blocks appear in reading-order, as best as can
be determined by layout analysis.


Each line in a block of text is a comma separated list of tokens, followed by an equal length list of IDs for each token.
At the end of the json file is an "id" section, which lists, every token id with the format

    [idnum, [pagenum, [top,left,width,height]]]

which describes the page number and bounding box for that particular token.

Some portions of a token may have special formatting information, like super- or sub-script annotation. If this is the case,
The token will begin and end with braces, like so:

    "{PhSiH_{3}}"

Within the token, superscripts are designated with ^{...}, subscripts with _{...}.

### Escaped Characters:

The following characters are always escaped:
   standard json escapes: '"', '\', ..
   braces: '{', '}'

Inside of a tex-formatted token, '_', '^' are also escaped.

