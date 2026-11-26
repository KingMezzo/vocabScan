# .github/ISSUE_TEMPLATE/bug_report.yml

name: "🐛 Bug Report"
description: Report a bug or unexpected behavior.
title: "[Bug]: "
labels: ["bug", "needs triage"]

body:
  - type: markdown
    attributes:
      value: |
        Thank you for helping improve GymMane Scanner!
        Please provide as much information as possible.

  - type: checkboxes
    id: checklist
    attributes:
      label: Before submitting
      options:
        - label: I have searched for existing issues.
          required: true
        - label: I am using the latest available release.
          required: false

  - type: input
    id: app-version
    attributes:
      label: GymMane Scanner version
      placeholder: "e.g. 2.1.0"
    validations:
      required: true

  - type: input
    id: gymmane-version
    attributes:
      label: GymMane version
      placeholder: "e.g. 2.4.0"
    validations:
      required: false

  - type: dropdown
    id: android-version
    attributes:
      label: Android version
      options:
        - Android 17
        - Android 16
        - Android 15
        - Android 14
        - Android 13
        - Android 12
        - Android 11
        - Android 10
        - Android 9
        - Android 8
        - Other / Unknown
    validations:
      required: true

  - type: input
    id: device
    attributes:
      label: Device manufacturer and model
      placeholder: "e.g. Samsung Galaxy S24"
    validations:
      required: true

  - type: dropdown
    id: component
    attributes:
      label: Affected component
      multiple: true
      options:
        - Barcode Scanner
        - Camera
        - Open Food Facts API
        - GymMane Import
        - Manual Entry
        - Scan History
        - Offline Cache
        - Settings
        - Update System
        - Installation
        - User Interface
        - Translations
        - Performance
        - Other
    validations:
      required: true

  - type: dropdown
    id: frequency
    attributes:
      label: How often does the issue occur?
      options:
        - Every time
        - Frequently
        - Occasionally
        - Only once
        - Unknown
    validations:
      required: true

  - type: dropdown
    id: impact
    attributes:
      label: Impact
      options:
        - Critical - App is unusable
        - High - Core functionality is broken
        - Medium - Feature does not work as expected
        - Low - Minor or visual issue
    validations:
      required: true

  - type: textarea
    id: description
    attributes:
      label: Describe the bug
      description: Explain what happened.
      placeholder: "When I scan a barcode..."
    validations:
      required: true

  - type: textarea
    id: reproduction
    attributes:
      label: Steps to reproduce
      placeholder: |
        1. Open GymMane Scanner
        2. Scan a product
        3. Tap ...
        4. Observe the issue
    validations:
      required: true

  - type: textarea
    id: expected
    attributes:
      label: Expected behavior
      description: What should have happened instead?
    validations:
      required: true

  - type: input
    id: barcode
    attributes:
      label: Product barcode
      description: If the issue affects a specific product.
      placeholder: "e.g. 4001234567890"
    validations:
      required: false

  - type: textarea
    id: screenshots
    attributes:
      label: Screenshots or screen recordings
      description: Attach relevant screenshots or recordings.
    validations:
      required: false

  - type: textarea
    id: logs
    attributes:
      label: Relevant logs
      description: Remove any personal information before submitting.
      render: shell
    validations:
      required: false

  - type: textarea
    id: additional-context
    attributes:
      label: Additional context
      description: Anything else that might help investigate the issue.
    validations:
      required: false

  - type: checkboxes
    id: privacy
    attributes:
      label: Privacy confirmation
      options:
        - label: I have removed personal information from the report and attachments.
          required: true
