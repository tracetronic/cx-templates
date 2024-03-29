# CX Templates

This repository contains a collection of templates for your CX automation workflows that may help you to find an easy way to get started with some specific tasks. CX stands for **"Continuous Everything"**.

**CX Templates** project is part of the [Automotive DevOps Platform](https://www.tracetronic.com/products/automotive-devops-platform/) by tracetronic. With the **Automotive DevOps Platform**, we go from the big picture to the details and unite all phases of vehicle software testing – from planning the test scopes to summarizing the test results. At the same time, continuous monitoring across all test phases always provides an overview of all activities – even with several thousand test executions per day and in different test environments.

## Table of Content

- [Project Structure](#project-structure)
- [Implementation](#usage)
  - [Use Cases](#use-cases)
  - [Technical Usage](#technical-usage)
- [Contribution](#contribution)
- [Support](#support)
- [License](#license)

## Project Structure

```text
(root)
+- Jenkins                          # tool based structure
|   +- CasC                         # semantic template structure
|      +- ...
|      +- README.md                 # template documentation
|   +- ...                          # semantic template structure
|   +- README.md                    # tool based documentation
+- ecu.test                         # tool based structure
+- test.guide                       # tool based structure
+- README.md                        # repository documentation
```

## Usage

### Use Cases

Each subfolder contains a `README.md` describing the use cases for the specific section. For a higher level description have a look at our [Automotive DevOps Platform](https://www.tracetronic.com/products/automotive-devops-platform/).

### Technical Usage

To use the templates all you have to do is to fork this repository or download any of those template folders or single files and adapt them to your needs.

## Contribution

We encourage you to contribute to **CX Templates** using the [issue tracker](https://github.com/tracetronic/cx-templates/issues/new/choose) to suggest feature requests and report bugs.

Currently, we do not accept any external pull requests.

## Support

If you have any further questions, please contact us at [support@tracetronic.com](mailto:support@tracetronic.com).

## License

This project is licensed under the terms of the [MIT license](LICENSE).
