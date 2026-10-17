import Modal from './Modal';

/**
 * Destructive-action confirmation. Deleting an application cascades to interviews, reminders and
 * history on the server, so the dialog spells out what will be lost before the request is sent.
 */
export default function ConfirmDialog({
  open,
  title = 'Are you sure?',
  message,
  detail,
  confirmLabel = 'Delete',
  cancelLabel = 'Cancel',
  tone = 'danger',
  busy = false,
  onConfirm,
  onCancel,
}) {
  return (
    <Modal
      open={open}
      title={title}
      onClose={busy ? undefined : onCancel}
      size="sm"
      footer={
        <>
          <button type="button" className="btn btn--ghost" onClick={onCancel} disabled={busy}>
            {cancelLabel}
          </button>
          <button
            type="button"
            className={`btn btn--${tone}`}
            onClick={onConfirm}
            disabled={busy}
            autoFocus
          >
            {busy ? 'Working…' : confirmLabel}
          </button>
        </>
      }
    >
      <p className="confirm__message">{message}</p>
      {detail ? <p className="confirm__detail">{detail}</p> : null}
    </Modal>
  );
}
