/** Empty and error states - the difference between a demo and a product. */
export default function EmptyState({ title, message, action, icon = '◇' }) {
  return (
    <div className="empty-state">
      <span className="empty-state__icon" aria-hidden="true">
        {icon}
      </span>
      <h3 className="empty-state__title">{title}</h3>
      {message ? <p className="empty-state__message">{message}</p> : null}
      {action ? <div className="empty-state__action">{action}</div> : null}
    </div>
  );
}

export function ErrorState({ message, onRetry }) {
  return (
    <div className="error-state" role="alert">
      <span className="error-state__icon" aria-hidden="true">
        !
      </span>
      <div>
        <h3 className="error-state__title">Something went wrong</h3>
        <p className="error-state__message">{message || 'The request could not be completed.'}</p>
      </div>
      {onRetry ? (
        <button type="button" className="btn btn--ghost btn--sm" onClick={onRetry}>
          Try again
        </button>
      ) : null}
    </div>
  );
}
