import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import './AuthPage.css'

type AuthPageProps = {
  mode: 'login' | 'forgot'
}

type ForgotStep = 1 | 2 | 3

const steps = [
  { label: 'Email', number: 1 },
  { label: 'Mã OTP', number: 2 },
  { label: 'Mật khẩu mới', number: 3 },
] as const

function BrandPanel() {
  return (
    <aside className="brand-panel">
      <div className="brand-lockup">
        <div className="brand-mark" aria-hidden="true">
          <span />
          <span />
          <span />
        </div>
        <span className="brand-name">Quản lý kho</span>
      </div>

      <div className="brand-copy">
        <h1>Quản lý tồn kho nhiều kho, chính xác đến từng serial</h1>
        <p>
          Nhập, xuất, chuyển kho, thuê mượn, kiểm kê và báo cáo trong một hệ
          thống.
        </p>
      </div>

      <p className="brand-footer">© Hệ thống quản lý kho</p>
      <div className="brand-orb brand-orb-large" aria-hidden="true" />
      <div className="brand-orb brand-orb-small" aria-hidden="true" />
    </aside>
  )
}

function StepIndicator({ active }: { active: ForgotStep }) {
  return (
    <ol className="step-indicator" aria-label="Các bước đặt lại mật khẩu">
      {steps.map((step, index) => {
        const complete = step.number < active
        const current = step.number === active

        return (
          <li
            className={`step-item${current ? ' is-active' : ''}${complete ? ' is-complete' : ''}`}
            aria-current={current ? 'step' : undefined}
            key={step.number}
          >
            <span className="step-number">{complete ? '✓' : step.number}</span>
            <span className="step-label">{step.label}</span>
            {index < steps.length - 1 && <span className="step-line" />}
          </li>
        )
      })}
    </ol>
  )
}

function AuthPage({ mode }: AuthPageProps) {
  const navigate = useNavigate()
  const [step, setStep] = useState<ForgotStep>(1)
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [email, setEmail] = useState('')
  const [otp, setOtp] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const forgotMode = mode === 'forgot'

  function showError(message: string) {
    setNotice('')
    setError(message)
  }

  function handleLogin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setNotice('')

    if (!username.trim() || !password) {
      showError('Vui lòng nhập tên đăng nhập/email và mật khẩu.')
      return
    }

    setNotice('Giao diện đăng nhập đã sẵn sàng; kết nối API sẽ được bổ sung sau khi chốt hợp đồng backend.')
  }

  function handleSendOtp(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setNotice('')

    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
      showError('Vui lòng nhập email hợp lệ.')
      return
    }

    setStep(2)
    setNotice('Mã OTP sẽ được gửi tới email đã đăng ký khi kết nối API.')
  }

  function handleVerifyOtp(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setNotice('')

    if (!/^\d{6}$/.test(otp)) {
      showError('Vui lòng nhập mã OTP gồm 6 chữ số.')
      return
    }

    setStep(3)
  }

  function handleResetPassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setNotice('')

    if (newPassword.length < 8) {
      showError('Mật khẩu mới cần có ít nhất 8 ký tự.')
      return
    }

    if (newPassword !== confirmPassword) {
      showError('Mật khẩu xác nhận chưa khớp.')
      return
    }

    setNotice('Biểu mẫu đã hợp lệ. Tính năng đặt lại mật khẩu sẽ hoạt động khi kết nối API.')
  }

  function handleResendOtp() {
    setError('')
    setNotice('Có thể gửi lại mã OTP sau khi kết nối API.')
  }

  function returnToLogin() {
    navigate('/login')
  }

  const title =
    !forgotMode ? 'Đăng nhập' : step === 1 ? 'Quên mật khẩu' : step === 2 ? 'Nhập mã OTP' : 'Đặt mật khẩu mới'
  const subtitle =
    !forgotMode
      ? 'Nhập tài khoản để vào hệ thống'
      : step === 1
        ? 'Nhập email đã đăng ký, hệ thống sẽ gửi mã OTP.'
        : step === 2
          ? 'Mã đã gửi tới email của bạn.'
          : 'Tạo mật khẩu mới cho tài khoản của bạn.'

  return (
    <main className="auth-page">
      <BrandPanel />
      <section className="auth-panel" aria-labelledby="auth-title">
        <div className="auth-content">
          <header className="auth-heading">
            <h2 id="auth-title">{title}</h2>
            <p>{subtitle}</p>
          </header>

          {forgotMode && <StepIndicator active={step} />}

          {!forgotMode && (
            <form className="auth-form" onSubmit={handleLogin} noValidate>
              <label className="form-field">
                <span>Tên đăng nhập hoặc email</span>
                <input
                  autoComplete="username"
                  placeholder="Nhập tên đăng nhập hoặc email"
                  value={username}
                  onChange={(event) => setUsername(event.target.value)}
                />
              </label>

              <label className="form-field">
                <span>Mật khẩu</span>
                <input
                  autoComplete="current-password"
                  placeholder="Nhập mật khẩu"
                  type="password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                />
              </label>

              <div className="form-link-row">
                <Link to="/forgot-password">Quên mật khẩu?</Link>
              </div>

              <Feedback error={error} notice={notice} />
              <button className="primary-button" type="submit">
                Đăng nhập
              </button>
              <p className="auth-helper">
                Chưa có tài khoản? Liên hệ quản trị viên để được cấp.
              </p>
            </form>
          )}

          {forgotMode && step === 1 && (
            <form className="auth-form" onSubmit={handleSendOtp} noValidate>
              <label className="form-field">
                <span>Email</span>
                <input
                  autoComplete="email"
                  placeholder="name@congty.com"
                  type="email"
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                />
              </label>

              <Feedback error={error} notice={notice} />
              <button className="primary-button" type="submit">
                Gửi mã OTP
              </button>
              <button className="text-button back-link" onClick={returnToLogin} type="button">
                ← Quay lại đăng nhập
              </button>
            </form>
          )}

          {forgotMode && step === 2 && (
            <form className="auth-form" onSubmit={handleVerifyOtp} noValidate>
              <label className="form-field">
                <span>Mã OTP</span>
                <input
                  autoComplete="one-time-code"
                  className="otp-input"
                  inputMode="numeric"
                  maxLength={6}
                  placeholder="Nhập mã 6 chữ số"
                  value={otp}
                  onChange={(event) => setOtp(event.target.value.replace(/\D/g, ''))}
                />
              </label>

              <div className="resend-row">
                <span>Gửi lại mã sau 60 giây</span>
                <button className="text-button" onClick={handleResendOtp} type="button">
                  Gửi lại mã
                </button>
              </div>

              <Feedback error={error} notice={notice} />
              <button className="primary-button" type="submit">
                Xác nhận
              </button>
              <button
                className="text-button back-link"
                onClick={() => {
                  setStep(1)
                  setError('')
                  setNotice('')
                }}
                type="button"
              >
                ← Đổi email
              </button>
            </form>
          )}

          {forgotMode && step === 3 && (
            <form className="auth-form" onSubmit={handleResetPassword} noValidate>
              <label className="form-field">
                <span>Mật khẩu mới</span>
                <input
                  autoComplete="new-password"
                  placeholder="Nhập mật khẩu mới"
                  type="password"
                  value={newPassword}
                  onChange={(event) => setNewPassword(event.target.value)}
                />
              </label>

              <label className="form-field">
                <span>Nhập lại mật khẩu</span>
                <input
                  autoComplete="new-password"
                  placeholder="Nhập lại mật khẩu"
                  type="password"
                  value={confirmPassword}
                  onChange={(event) => setConfirmPassword(event.target.value)}
                />
              </label>

              <p className="password-hint">
                Mật khẩu cần đạt quy tắc theo SRS (độ dài, ký tự).
              </p>
              <Feedback error={error} notice={notice} />
              <button className="primary-button" type="submit">
                Đổi mật khẩu
              </button>
            </form>
          )}
        </div>
      </section>
    </main>
  )
}

function Feedback({ error, notice }: { error: string; notice: string }) {
  if (!error && !notice) return null

  return (
    <p className={`form-feedback${error ? ' is-error' : ' is-notice'}`} role={error ? 'alert' : 'status'}>
      {error || notice}
    </p>
  )
}

export default AuthPage
