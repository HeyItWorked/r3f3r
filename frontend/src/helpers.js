// helpers

// API -> row. converts YYYY-MM-DD -> DD.MM.YYYY
export function normalize(r) {
  if (r == null) return r
  var d = r.followUpDate || ''
  var tmp = {}
  tmp.id = r.id
  tmp.patientRef = r.patientReference || ''
  tmp.office = r.specialistOffice || ''
  tmp.followUp = /^\d{4}-\d{2}-\d{2}$/.test(d) ? d.slice(8, 10) + '.' + d.slice(5, 7) + '.' + d.slice(0, 4) : d
  tmp.followUpRaw = d                      // for sorting
  tmp.status = r.status || ''
  tmp.overdue = !!r.overdue
  tmp.daysUntil = r.daysUntilFollowUp      // from the backend, dont do date math here
  tmp.daysOverdue = r.daysOverdue || 0
  return tmp
}

export function attentionText(r) {
  if (r.daysOverdue > 0) {
    if (r.daysOverdue == 1) {
      return '1 day overdue'
    } else {
      return r.daysOverdue + ' days overdue'
    }
  }
  return ''
}

export function fmtTime(iso) {
  var d = new Date(iso)
  return isNaN(d.getTime()) ? iso : d.toLocaleString()
}

// get the error message out of whatever came back
export function errMsg(e, fallback, field) {
  var b = e && e.body
  return (b && field && b.fieldErrors && b.fieldErrors[field]) || (b && b.message) || (e && e.message) || fallback
}

// not used anymore
export function formatDateOld(d) {
  var parts = d.split('-')
  return parts[2] + '/' + parts[1] + '/' + parts[0]
}
