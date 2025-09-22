2025-09-22T19:45:44.3627013Z ##[group]Run if [ ! -f "README.md" ]; then
2025-09-22T19:45:44.3628741Z [36;1mif [ ! -f "README.md" ]; then[0m
2025-09-22T19:45:44.3630060Z [36;1m  echo "❌ README.md is missing"[0m
2025-09-22T19:45:44.3631291Z [36;1m  exit 1[0m
2025-09-22T19:45:44.3632252Z [36;1mfi[0m
2025-09-22T19:45:44.3633165Z [36;1m[0m
2025-09-22T19:45:44.3634163Z [36;1mreadme_length=$(wc -l < README.md)[0m
2025-09-22T19:45:44.3635513Z [36;1mif [ "$readme_length" -lt 20 ]; then[0m
2025-09-22T19:45:44.3637018Z [36;1m  echo "❌ README.md is too short (less than 20 lines)"[0m
2025-09-22T19:45:44.3638659Z [36;1m  exit 1[0m
2025-09-22T19:45:44.3639619Z [36;1mfi[0m
2025-09-22T19:45:44.3640524Z [36;1m[0m
2025-09-22T19:45:44.3641626Z [36;1mecho "✅ README.md exists and is comprehensive"[0m
2025-09-22T19:45:44.3697913Z shell: /usr/bin/bash -e {0}
2025-09-22T19:45:44.3699301Z ##[endgroup]
2025-09-22T19:45:44.4015489Z ✅ README.md exists and is comprehensive
